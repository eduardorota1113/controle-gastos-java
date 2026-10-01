'use strict';

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const money = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(value));
const number = value => new Intl.NumberFormat('pt-BR').format(value);
const localDate = (date = new Date()) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const monthName = month => new Date(`${month}-15T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
const dateLabel = date => new Date(`${date}T12:00:00`).toLocaleDateString('pt-BR');
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const icon = name => `<svg class="icon" aria-hidden="true"><use href="#i-${name}"/></svg>`;
const methods = { PIX: 'Pix', DEBITO: 'Débito', CREDITO: 'Crédito', DINHEIRO: 'Dinheiro', BOLETO: 'Boleto', TRANSFERENCIA: 'Transferência' };
const categoryPalette = {
  'Alimentação': ['#f59e0b', '#b78c47'],
  'Moradia': ['#6366f1', '#60745a'],
  'Transporte': ['#06b6d4', '#647e82'],
  'Saúde': ['#f43f5e', '#ad6553'],
  'Lazer': ['#8b5cf6', '#88727c'],
  'Educação': ['#14b8a6', '#7f8b64'],
  'Outros': ['#64748b', '#8d8776']
};
// Só as cores iniciais ganham o tom do caderno. As que você escolheu continuam suas.
const categoryInk = (color, name) => categoryPalette[name]?.[0] === color.toLowerCase() ? categoryPalette[name][1] : color;
const state = { view: 'overview', month: localDate().slice(0, 7), categories: [], dashboard: null, expenses: [], page: 0, total: 0, filters: {}, request: 0, busy: false };
let toastTimer;
let confirmAction;
let budgetMonth;

function period(month) {
  const [year, monthNumber] = month.split('-').map(Number);
  const last = new Date(year, monthNumber, 0).getDate();
  return { from: `${month}-01`, to: `${month}-${last}` };
}

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(`/api${path}`, { cache: 'no-store', ...options, headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers } });
  } catch {
    throw new Error('Não foi possível conectar ao sistema. Verifique se o servidor está em execução.');
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(body.message || `Não foi possível concluir a operação (HTTP ${response.status}).`);
    error.fields = body.fields || {};
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast span').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 4500);
}

function showError(element, error) {
  const details = Object.values(error.fields || {});
  element.textContent = details.length ? details.join(' ') : error.message;
  element.hidden = false;
}

function globalError(error) {
  $('#global-error span').textContent = error.message;
  $('#global-error').hidden = false;
}

function setView(view) {
  state.view = ['overview', 'expenses', 'categories'].includes(view) ? view : 'overview';
  state.page = 0;
  const titles = {
    overview: ['Seu mês,', 'sem rodeios.', 'O que você gastou. E quanto ainda cabe no mês.', 'Visão geral'],
    expenses: ['Cada gasto,', 'por escrito.', 'Do almoço de hoje à conta que chega todo mês.', 'Gastos'],
    categories: ['Cada coisa,', 'no seu lugar.', 'Dê um nome às coisas. Depois fica fácil achar.', 'Categorias']
  };
  const [title, accent, subtitle, breadcrumb] = titles[state.view];
  document.body.dataset.view = state.view;
  $('#title-line').textContent = title;
  $('#title-accent').textContent = accent;
  $('#breadcrumb').textContent = breadcrumb;
  $('#page-subtitle').textContent = subtitle;
  $('#main-add span').textContent = state.view === 'categories' ? 'Nova categoria' : 'Novo gasto';
  $('#overview-view').hidden = state.view !== 'overview';
  $('#expenses-section').hidden = state.view === 'categories';
  $('#categories-view').hidden = state.view !== 'categories';
  $('#month-toolbar').hidden = state.view === 'categories';
  $('#filters').hidden = state.view !== 'expenses';
  $('#export-csv').hidden = state.view !== 'expenses';
  $('#pagination').hidden = state.view !== 'expenses';
  $('#view-all').hidden = state.view !== 'overview';
  $('#table-title').textContent = state.view === 'overview' ? 'Os últimos registros.' : 'Linha por linha.';
  $('#table-subtitle').textContent = state.view === 'overview' ? 'Do mais recente para trás.' : 'Busque pelo nome ou recorte o período.';
  $$('.nav-item').forEach(button => {
    button.classList.toggle('active', button.dataset.view === state.view);
    if (button.dataset.view === state.view) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  history.replaceState(null, '', `#${state.view}`);
  refresh();
}

function updateMonth(month) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || Number(month.slice(0, 4)) < 1900 || Number(month.slice(0, 4)) > 9998) return;
  state.month = month;
  state.page = 0;
  state.filters = period(month);
  $('#selected-month').value = month;
  $('#month-label').textContent = monthName(month);
  $('#filter-from').value = state.filters.from;
  $('#filter-to').value = state.filters.to;
  $('#filter-search').value = '';
  $('#filter-category').value = '';
  $('#previous-month').disabled = month === '1900-01';
  $('#next-month').disabled = month === '9998-12';
}

function expenseParams() {
  const filters = state.view === 'overview' ? period(state.month) : state.filters;
  return new URLSearchParams({ ...filters, page: state.view === 'overview' ? 0 : state.page, size: state.view === 'overview' ? 5 : 10 });
}

async function refresh() {
  const sequence = ++state.request;
  const view = state.view;
  const month = state.month;
  const params = expenseParams();
  $('#global-error').hidden = true;
  $('#main-add').disabled = true;
  $('#expenses-section').setAttribute('aria-busy', 'true');
  try {
    const [categories, dashboard, page] = await Promise.all([
      api('/categories'),
      view === 'categories' ? Promise.resolve(null) : api(`/dashboard?month=${month}`),
      view === 'categories' ? Promise.resolve(null) : api(`/expenses?${params}`)
    ]);
    if (sequence !== state.request) return;
    state.categories = categories;
    renderCategories();
    if (dashboard) { state.dashboard = dashboard; renderDashboard(dashboard); }
    if (page) {
      state.expenses = page.items;
      state.total = page.totalElements;
      // If deleting the last item on a page made it empty, return to the previous page.
      if (view === 'expenses' && page.items.length === 0 && state.page > 0 && page.totalElements > 0) {
        state.page = Math.max(0, Math.ceil(page.totalElements / 10) - 1);
        return refresh();
      }
      renderExpenses(page);
    }
  } catch (error) {
    if (sequence === state.request) globalError(error);
  } finally {
    if (sequence === state.request) {
      $('#main-add').disabled = false;
      $('#expenses-section').removeAttribute('aria-busy');
    }
  }
}

function renderCategories() {
  const selected = $('#filter-category').value;
  const options = state.categories.map(category => `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join('');
  $('#filter-category').innerHTML = `<option value="">Todas as categorias</option>${options}`;
  if (state.categories.some(category => String(category.id) === selected)) $('#filter-category').value = selected;
  $('#category-list').innerHTML = state.categories.length ? state.categories.map(category => {
    const color = categoryInk(category.color, category.name);
    return `<article class="category-card"><span class="expense-icon" style="color:${color};background:${color}18">${icon('tag')}</span><div class="category-details"><strong>${escapeHtml(category.name)}</strong><small>${number(category.expenseCount)} ${category.expenseCount === 1 ? 'gasto vinculado' : 'gastos vinculados'}</small></div><div class="row-actions"><button class="icon-button" data-edit-category="${category.id}" aria-label="Editar ${escapeHtml(category.name)}">${icon('edit')}</button><button class="icon-button" data-delete-category="${category.id}" aria-label="Excluir ${escapeHtml(category.name)}">${icon('trash')}</button></div></article>`;
  }).join('') : `<div class="panel empty-state">${icon('tag')}<strong>Cada coisa com seu nome.</strong><p>Use “Nova categoria” para começar.</p></div>`;
}

function renderDashboard(data) {
  $('#stat-total').textContent = money(data.total);
  $('#stat-count').textContent = number(data.count);
  $('#stat-average').textContent = data.count ? `${money(Number(data.total) / data.count)} em média por gasto` : 'Comece pela primeira anotação.';
  const previous = Number(data.previousTotal);
  const difference = previous ? ((Number(data.total) - previous) / previous) * 100 : null;
  $('#stat-comparison').textContent = difference === null ? 'Sem gastos no mês anterior' : difference === 0 ? 'Mesmo total do mês anterior' : `${Math.abs(difference).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% ${difference < 0 ? 'a menos' : 'a mais'} que o mês anterior`;
  const hasBudget = data.budget !== null;
  const over = hasBudget && Number(data.remaining) < 0;
  $('#stat-remaining').textContent = hasBudget ? money(data.remaining) : 'Sem orçamento';
  $('#stat-remaining').classList.toggle('negative', over);
  $('#open-budget').textContent = hasBudget ? 'Editar' : 'Definir';
  const percentage = hasBudget ? Number(data.total) / Number(data.budget) * 100 : 0;
  $('#budget-progress').style.width = `${Math.min(percentage, 100)}%`;
  $('#budget-progress').style.background = over ? '#a74f3e' : '#7c8a65';
  $('#stat-budget-caption').textContent = hasBudget ? over ? `Limite de ${money(data.budget)} ultrapassado` : `${Math.round(percentage)}% do orçamento de ${money(data.budget)}` : 'Quanto você quer gastar neste mês?';
  renderCategoryChart(data);
  renderHistoryChart(data.history);
}

function renderCategoryChart(data) {
  if (!Number(data.total)) {
    $('#category-chart').innerHTML = `<div class="empty-chart"><div>${icon('tag')}<p>Ainda não tem o que dividir.<br>Registre o primeiro gasto.</p></div></div>`;
    return;
  }
  const groups = data.byCategory.slice(0, 5).map(category => ({ ...category, color: categoryInk(category.color, category.name) }));
  if (data.byCategory.length > 5) groups.push({ name: 'Demais categorias', color: '#8d8776', total: data.byCategory.slice(5).reduce((sum, category) => sum + Number(category.total), 0) });
  let offset = 0;
  const gradient = groups.map(category => {
    const start = offset;
    offset += Number(category.total) / Number(data.total) * 100;
    return `${category.color} ${start.toFixed(4)}% ${offset.toFixed(4)}%`;
  }).join(',');
  $('#category-chart').innerHTML = `<div class="donut" style="background:conic-gradient(${gradient})" role="img" aria-label="Distribuição dos gastos por categoria"><div class="donut-center"><small>CATEGORIAS</small><strong>${data.byCategory.length}</strong></div></div><div class="category-legend">${groups.map(category => `<div class="legend-row" title="${escapeHtml(category.name)}: ${money(category.total)}"><div class="legend-name"><span class="color-dot" style="background:${category.color}"></span><span>${escapeHtml(category.name)}</span></div><span class="legend-value">${(Number(category.total) / Number(data.total) * 100).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span></div>`).join('')}</div>`;
}

function renderHistoryChart(items) {
  const maximum = Math.max(...items.map(item => Number(item.total)), 1);
  const compact = value => value >= 1000 ? `${(value / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil` : Math.round(value).toLocaleString('pt-BR');
  const grid = [0, 0.5, 1].map(ratio => `<line class="chart-grid" x1="47" x2="480" y1="${159 - ratio * 120}" y2="${159 - ratio * 120}"/><text class="chart-label" x="38" y="${163 - ratio * 120}" text-anchor="end">${compact(maximum * ratio)}</text>`).join('');
  const bars = items.map((item, index) => {
    const height = Number(item.total) / maximum * 120;
    const x = 69 + index * 68;
    const label = new Date(`${item.month}-15T12:00:00`).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '');
    return `<g><title>${monthName(item.month)}: ${money(item.total)}</title><rect x="${x}" y="${159 - height}" width="35" height="${Math.max(height, 2)}" rx="1" fill="${index === 5 ? '#344c3b' : '#a6b094'}"/><text class="chart-label" x="${x + 17.5}" y="184" text-anchor="middle">${label}</text></g>`;
  }).join('');
  $('#history-chart').innerHTML = `<svg viewBox="0 0 500 195" role="img" aria-label="Gastos dos últimos seis meses em reais"><text class="chart-label" x="6" y="16">R$</text>${grid}${bars}</svg>`;
}

function renderExpenses(page) {
  $('#expense-rows').innerHTML = page.items.length ? page.items.map(expense => {
    const color = categoryInk(expense.categoryColor, expense.categoryName);
    return `<tr><td><div class="expense-description"><span class="expense-icon" style="color:${color};background:${color}18">${icon('wallet')}</span><span class="expense-text" title="${escapeHtml(expense.description)}">${escapeHtml(expense.description)}</span></div></td><td><span class="category-pill"><span class="color-dot" style="background:${color}"></span>${escapeHtml(expense.categoryName)}</span></td><td class="date-cell">${dateLabel(expense.date)}</td><td class="payment-cell">${methods[expense.paymentMethod]}</td><td class="amount-column">${money(expense.amount)}</td><td class="actions-column"><div class="row-actions"><button class="icon-button" data-edit-expense="${expense.id}" aria-label="Editar ${escapeHtml(expense.description)}">${icon('edit')}</button><button class="icon-button" data-delete-expense="${expense.id}" aria-label="Excluir ${escapeHtml(expense.description)}">${icon('trash')}</button></div></td></tr>`;
  }).join('') : `<tr><td colspan="6" class="empty-state">${icon('wallet')}<strong>${state.view === 'expenses' ? 'Nada por aqui.' : 'Uma página em branco.'}</strong><p>${state.view === 'expenses' ? 'Tente outro filtro ou anote um novo gasto.' : 'Seu primeiro registro começa em “Novo gasto”.'}</p></td></tr>`;
  $('#table-count').textContent = state.view === 'overview' ? `${page.items.length} de ${number(page.totalElements)} gastos do mês` : `${number(page.totalElements)} ${page.totalElements === 1 ? 'gasto encontrado' : 'gastos encontrados'} · Total: ${money(page.totalAmount)}`;
  $('#page-info').textContent = `${state.page + 1} / ${Math.max(1, Math.ceil(page.totalElements / 10))}`;
  $('#previous-page').disabled = state.page === 0;
  $('#next-page').disabled = (state.page + 1) * 10 >= page.totalElements;
}

function openExpense(expense) {
  if (!state.categories.length) { toast('Crie uma categoria antes de registrar um gasto.'); setView('categories'); return; }
  const form = $('#expense-form');
  form.reset();
  $('#expense-error').hidden = true;
  $('#expense-dialog-title').textContent = expense ? 'Editar gasto' : 'Novo gasto';
  form.elements.categoryId.innerHTML = state.categories.map(category => `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join('');
  form.elements.id.value = expense?.id || '';
  form.elements.description.value = expense?.description || '';
  form.elements.amount.value = expense?.amount ?? '';
  form.elements.date.value = expense?.date || (state.month === localDate().slice(0, 7) ? localDate() : `${state.month}-01`);
  form.elements.categoryId.value = expense?.categoryId || state.categories[0].id;
  form.elements.paymentMethod.value = expense?.paymentMethod || 'PIX';
  form.elements.notes.value = expense?.notes || '';
  $('#expense-dialog').showModal();
  form.elements.description.focus();
}

function openCategory(category) {
  const form = $('#category-form');
  form.reset();
  $('#category-error').hidden = true;
  $('#category-dialog-title').textContent = category ? 'Editar categoria' : 'Nova categoria';
  form.elements.id.value = category?.id || '';
  form.elements.name.value = category?.name || '';
  form.elements.color.value = category ? categoryInk(category.color, category.name) : '#7f8b64';
  $('#category-dialog').showModal();
  form.elements.name.focus();
}

function confirmDelete(title, description, action) {
  confirmAction = action;
  $('#confirm-title').textContent = title;
  $('#confirm-description').textContent = description;
  $('#confirm-error').hidden = true;
  $('#confirm-dialog').showModal();
}

async function saveForm(event, dialog, errorElement, action, message) {
  event.preventDefault();
  if (state.busy) return;
  state.busy = true;
  const button = event.target.querySelector('[type=submit]');
  button.disabled = true;
  errorElement.hidden = true;
  dialog.dataset.saving = 'true';
  try {
    await action();
    dialog.close();
    toast(message);
    await refresh();
  } catch (error) { showError(errorElement, error); }
  finally { state.busy = false; button.disabled = false; delete dialog.dataset.saving; }
}

$$('.nav-item').forEach(button => button.addEventListener('click', () => setView(button.dataset.view)));
window.addEventListener('hashchange', () => setView(location.hash.slice(1)));
$('#view-all').addEventListener('click', () => setView('expenses'));
$('#main-add').addEventListener('click', () => state.view === 'categories' ? openCategory() : openExpense());
$('#retry').addEventListener('click', refresh);
$('#selected-month').addEventListener('change', event => { updateMonth(event.target.value); refresh(); });
for (const [selector, offset] of [['#previous-month', -1], ['#next-month', 1]]) {
  $(selector).addEventListener('click', () => {
    const [year, month] = state.month.split('-').map(Number);
    const date = new Date(year, month - 1 + offset, 15);
    updateMonth(localDate(date).slice(0, 7));
    refresh();
  });
}
$('#filters').addEventListener('submit', event => {
  event.preventDefault();
  if ($('#filter-from').value > $('#filter-to').value) { globalError(new Error('A data inicial deve ser anterior ou igual à data final.')); return; }
  state.filters = Object.fromEntries(new FormData(event.target).entries());
  for (const key of Object.keys(state.filters)) if (!state.filters[key]) delete state.filters[key];
  state.page = 0;
  refresh();
});
$('#reset-filters').addEventListener('click', () => { updateMonth(state.month); refresh(); });
$('#previous-page').addEventListener('click', () => { if (state.page > 0) { state.page--; refresh(); } });
$('#next-page').addEventListener('click', () => { if ((state.page + 1) * 10 < state.total) { state.page++; refresh(); } });
$('#export-csv').addEventListener('click', async () => {
  const button = $('#export-csv');
  button.disabled = true;
  try {
    const params = new URLSearchParams(state.filters);
    const response = await fetch(`/api/expenses/export?${params}`);
    if (!response.ok) { const body = await response.json(); throw new Error(body.message); }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `gastos-${state.filters.from}-a-${state.filters.to}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast('Arquivo CSV exportado.');
  } catch (error) { globalError(error); }
  finally { button.disabled = false; }
});
$$('[data-close]').forEach(button => button.addEventListener('click', () => { const dialog = $(`#${button.dataset.close}`); if (!dialog.dataset.saving) dialog.close(); }));
$$('dialog').forEach(dialog => dialog.addEventListener('cancel', event => { if (dialog.dataset.saving) event.preventDefault(); }));
$('#expense-rows').addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  const expense = state.expenses.find(item => item.id === Number(button.dataset.editExpense || button.dataset.deleteExpense));
  if (!expense) return;
  if (button.dataset.editExpense) openExpense(expense);
  else confirmDelete('Excluir gasto?', `“${expense.description}”, no valor de ${money(expense.amount)}, será removido. Esta ação não pode ser desfeita.`, () => api(`/expenses/${expense.id}`, { method: 'DELETE' }));
});
$('#category-list').addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  const category = state.categories.find(item => item.id === Number(button.dataset.editCategory || button.dataset.deleteCategory));
  if (!category) return;
  if (button.dataset.editCategory) openCategory(category);
  else if (category.expenseCount > 0) toast('Mova ou exclua os gastos desta categoria antes de removê-la.');
  else confirmDelete('Excluir categoria?', `A categoria “${category.name}” será removida.`, () => api(`/categories/${category.id}`, { method: 'DELETE' }));
});
$('#expense-form').addEventListener('submit', event => {
  const values = Object.fromEntries(new FormData(event.target));
  const id = values.id;
  delete values.id;
  values.categoryId = Number(values.categoryId);
  saveForm(event, $('#expense-dialog'), $('#expense-error'), () => api(`/expenses${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(values) }), id ? 'Gasto atualizado.' : 'Gasto registrado.');
});
$('#category-form').addEventListener('submit', event => {
  const values = Object.fromEntries(new FormData(event.target));
  const id = values.id;
  delete values.id;
  saveForm(event, $('#category-dialog'), $('#category-error'), () => api(`/categories${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(values) }), id ? 'Categoria atualizada.' : 'Categoria criada.');
});
$('#open-budget').addEventListener('click', () => {
  budgetMonth = state.month;
  $('#budget-error').hidden = true;
  $('#budget-month-description').textContent = `Defina um limite para ${monthName(budgetMonth)}. Ele ajuda você a acompanhar quanto ainda pode gastar.`;
  $('#budget-form').elements.amount.value = state.dashboard?.budget ?? '';
  $('#remove-budget').hidden = state.dashboard?.budget == null;
  $('#budget-dialog').showModal();
});
$('#budget-form').addEventListener('submit', event => saveForm(event, $('#budget-dialog'), $('#budget-error'), () => api(`/budgets/${budgetMonth}`, { method: 'PUT', body: JSON.stringify({ amount: event.target.elements.amount.value }) }), 'Orçamento atualizado.'));
$('#remove-budget').addEventListener('click', () => {
  $('#budget-dialog').close();
  confirmDelete('Remover orçamento?', `O limite de ${monthName(budgetMonth)} será removido. Seus gastos continuarão registrados.`, () => api(`/budgets/${budgetMonth}`, { method: 'DELETE' }));
});
$('#confirm-delete').addEventListener('click', async () => {
  if (state.busy || !confirmAction) return;
  state.busy = true;
  const button = $('#confirm-delete');
  const dialog = $('#confirm-dialog');
  button.disabled = true;
  dialog.dataset.saving = 'true';
  try { await confirmAction(); dialog.close(); toast('Registro removido.'); await refresh(); }
  catch (error) { showError($('#confirm-error'), error); }
  finally { state.busy = false; button.disabled = false; delete dialog.dataset.saving; }
});

updateMonth(state.month);
api('/info').then(info => { $('#demo-banner').hidden = !info.demo; }).catch(globalError);
setView(location.hash.slice(1) || 'overview');
