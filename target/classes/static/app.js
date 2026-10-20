
const state = {
  user: JSON.parse(localStorage.getItem("expenseUser") || "null"),
  type: "EXPENSE",
  categories: { INCOME: [], EXPENSE: [] },
  transactions: [],
  summary: null,
  editingId: null
};


const $ = id => document.getElementById(id);

function money(n) {
  return new Intl.NumberFormat("en-IN", {
    style:"currency", currency:"INR", maximumFractionDigits:0
  }).format(n || 0);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, c => ({
    "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
  }[c]));
}

function showToast(message) {
  const t = $("toast");
  t.textContent = message;
  t.classList.add("show");
  setTimeout(() => t.classList.remove("show"), 2300);
}

async function api(url, options={}) {
  const response = await fetch(url, {
    ...options,
    headers: {"Content-Type":"application/json", ...(options.headers || {})}
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "Something went wrong");
  return data;
}

async function login(event) {
  event.preventDefault();

  try {
    const user = await api("/api/login", {
      method:"POST",
      body:JSON.stringify({
        email:$("email").value,
        password:$("password").value
      })
    });

    state.user = user;
    localStorage.setItem("expenseUser", JSON.stringify(user));
    showApp();
  } catch (error) {
    showToast(error.message);
  }
}

async function showApp() {
  $("loginScreen").classList.add("hidden");
  $("app").classList.remove("hidden");

  $("username").textContent = state.user.name;
  $("avatar").textContent = state.user.name[0].toUpperCase();

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  $("greeting").textContent = `${greeting}, ${state.user.name.split(" ")[0]}`;

  await loadCategories();
  await refresh();
}

async function loadCategories() {
  state.categories.INCOME = await api("/api/categories?type=INCOME");
  state.categories.EXPENSE = await api("/api/categories?type=EXPENSE");
}

async function refresh() {
  state.summary = await api(`/api/summary?userId=${state.user.userId}`);
  state.transactions = await api(`/api/transactions?userId=${state.user.userId}`);
  renderOverview();
  renderTransactions();
  renderAnalytics();
}

function renderOverview() {
  const s = state.summary;

  $("balance").textContent = money(s.balance);
  $("income").textContent = money(s.income);
  $("expense").textContent = money(s.expense);

  const rate = s.income ? Math.round((s.balance / s.income) * 100) : 0;
  $("savingRate").textContent = rate + "%";

  renderBars($("categoryBars"), s.byCategory);

  $("recent").innerHTML =
    state.transactions.slice(0, 5).map(transactionHtml).join("") ||
    `<p style="color:#9aa39e;font-size:11px">No transactions yet.</p>`;

  // Display the monthly income-versus-expense chart
  renderMonthlyComparison();
}

function renderBars(container, categories) {
  if (!categories.length) {
    container.innerHTML = `<p style="color:#9aa39e;font-size:11px">No expense data yet.</p>`;
    return;
  }

  const max = Math.max(...categories.map(c => c.total), 1);

  container.innerHTML = categories.map(c => `
    <div class="bar">
      <div class="bar-label">
        <span>${escapeHtml(c.icon)} ${escapeHtml(c.name)}</span>
        <b>${money(c.total)}</b>
      </div>
      <div class="track">
        <div class="fill" style="width:${(c.total/max)*100}%"></div>
      </div>
    </div>
  `).join("");
}


function renderMonthlyComparison() {
  const container = $("monthlyComparison");
  if (!container) return;

  // Show the latest six months, including months with no transactions.
  const months = [];
  const now = new Date();

  for (let i = 5; i >= 0; i--) {
    const date = new Date(now.getFullYear(), now.getMonth() - i, 1);

    months.push({
      key: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`,
      label: date.toLocaleDateString("en-IN", { month: "short" }),
      income: 0,
      expense: 0
    });
  }

  const monthMap = new Map(months.map(month => [month.key, month]));

  state.transactions.forEach(t => {
    const date = new Date(`${t.date}T00:00:00`);
    if (Number.isNaN(date.getTime())) return;

    const key =
      `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;

    const month = monthMap.get(key);
    if (!month) return;

    if (t.type === "INCOME") {
      month.income += Number(t.amount) || 0;
    } else if (t.type === "EXPENSE") {
      month.expense += Number(t.amount) || 0;
    }
  });

  const max = Math.max(
    1,
    ...months.flatMap(m => [m.income, m.expense])
  );

  container.innerHTML = `
    <div class="chart-legend">
      <span><i class="legend-income"></i> Income</span>
      <span><i class="legend-expense"></i> Expenses</span>
    </div>

    ${months.map(m => `
      <div class="monthly-row">
        <div class="monthly-label">${escapeHtml(m.label)}</div>

        <div class="monthly-bars">
          <div class="monthly-track"
               title="Income: ${escapeHtml(money(m.income))}">
            <div class="monthly-fill income"
                 style="width:${m.income / max * 100}%"></div>
          </div>

          <div class="monthly-track"
               title="Expenses: ${escapeHtml(money(m.expense))}">
            <div class="monthly-fill expense"
                 style="width:${m.expense / max * 100}%"></div>
          </div>
        </div>
      </div>
    `).join("")}
  `;
}


function transactionHtml(t) {
  const income = t.type === "INCOME";

  return `
    <div class="activity">
      <div class="activity-icon">${escapeHtml(t.icon)}</div>
      <div class="activity-main">
        <b>${escapeHtml(t.description || t.category)}</b>
        <span>${escapeHtml(t.category)} · ${formatDate(t.date)}</span>
      </div>
      <div class="amount ${income ? "income-color" : "expense-color"}">
        ${income ? "+" : "−"}${money(t.amount)}
      </div>
    </div>
  `;
}

function renderTransactions() {
  const search = ($("search").value || "").toLowerCase();
  const filter = $("typeFilter").value;
  const sort = $("sortFilter")?.value || "NEWEST";

  const list = state.transactions.filter(t => {
  const matchesType = filter === "ALL" || t.type === filter;
  const text = `${t.description || ""} ${t.category || ""}`.toLowerCase();

  return matchesType && text.includes(search);
});

list.sort((a, b) => {
  if (sort === "OLDEST") {
    return new Date(a.date) - new Date(b.date);
  }

  if (sort === "AMOUNT_HIGH") {
    return Number(b.amount) - Number(a.amount);
  }

  if (sort === "AMOUNT_LOW") {
    return Number(a.amount) - Number(b.amount);
  }

  return new Date(b.date) - new Date(a.date);
});

  $("tableBody").innerHTML = list.map(t => `
    <tr>
      <td><b>${escapeHtml(t.description || "Untitled")}</b></td>
      <td>${escapeHtml(t.icon)} ${escapeHtml(t.category)}</td>
      <td>${formatDate(t.date)}</td>
      <td><span class="tag ${t.type}">${t.type}</span></td>
      <td class="${t.type === "INCOME" ? "income-color" : "expense-color"}">
        <b>${t.type === "INCOME" ? "+" : "−"}${money(t.amount)}</b>
      </td>
      
<td>
  <button class="action" onclick="editTransaction(${t.id})">Edit</button>
  <button class="action" onclick="removeTransaction(${t.id})">Delete</button>
</td>

    </tr>
  `).join("") || `<tr><td colspan="6" style="color:#9aa39e">No matching transactions.</td></tr>`;
}

function renderAnalytics() {
  renderBars($("analyticsBars"), state.summary.byCategory);

  const top = state.summary.byCategory[0];
  const rate = state.summary.income
    ? Math.round((state.summary.balance / state.summary.income) * 100)
    : 0;

  $("insights").innerHTML = `
    <div class="insight">
      <b>Largest spending category</b>
      ${top ? `${escapeHtml(top.name)} — ${money(top.total)}` : "No expense data yet."}
    </div>
    <div class="insight">
      <b>Savings rate</b>
      You currently keep ${rate}% of recorded income.
    </div>
    <div class="insight">
      <b>Total transactions</b>
      ${state.transactions.length} transaction(s) are recorded.
    </div>
  `;
}

function formatDate(date) {
  return new Date(date + "T00:00:00").toLocaleDateString("en-IN", {
    day:"2-digit", month:"short", year:"numeric"
  });
}

function openModal(type) {
  state.editingId = null;
  state.type = type;
  $("modal").classList.remove("hidden");
  $("modalTitle").textContent = type === "INCOME" ? "Add income" : "Add expense";
  $("amount").value = "";
  $("description").value = "";
  $("date").value = new Date().toISOString().slice(0,10);
  setType(type);
  $("modal").querySelector("form button[type='submit']").innerHTML =
  'Save transaction <b>→</b>';
}

function closeModal() {
  $("modal").classList.add("hidden");
}

function setType(type) {
  state.type = type;
  $("chooseExpense").classList.toggle("selected", type === "EXPENSE");
  $("chooseIncome").classList.toggle("selected", type === "INCOME");

  const list = state.categories[type];
  $("category").innerHTML = list.map(c =>
    `<option value="${c.id}">${escapeHtml(c.icon)} ${escapeHtml(c.name)}</option>`
  ).join("");
}

async function saveTransaction(event) {
  event.preventDefault();

  try {
    
const editing = state.editingId !== null;

await api(
  editing
    ? `/api/transactions/${state.editingId}`
    : "/api/transactions",
  {
    method: editing ? "PUT" : "POST",
    body: JSON.stringify({
      userId: String(state.user.userId),
      categoryId: $("category").value,
      amount: $("amount").value,
      type: state.type,
      description: $("description").value,
      date: $("date").value
    })
  }
);


    closeModal();
    showToast("Transaction saved successfully");
    await refresh();
  } catch (error) {
    showToast(error.message);
  }
}

async function removeTransaction(id) {
  if (!confirm("Delete this transaction?")) return;

  try {
    await api(`/api/transactions/${id}?userId=${state.user.userId}`, {
      method:"DELETE"
    });
    showToast("Transaction deleted");
    await refresh();
  } catch (error) {
    showToast(error.message);
  }
}

window.removeTransaction = removeTransaction;


function editTransaction(id) {
  const t = state.transactions.find(item => Number(item.id) === Number(id));

  if (!t) {
    showToast("Transaction not found");
    return;
  }

  state.editingId = t.id;
  state.type = t.type;

  $("modalTitle").textContent = "Edit transaction";
  $("modal").classList.remove("hidden");

  $("amount").value = t.amount;
  $("description").value = t.description || "";
  $("date").value = t.date;

  setType(t.type);
  $("category").value = String(t.categoryId);

  // Change the modal heading and button for edit mode.
  $("modal").querySelector("form button[type='submit']").innerHTML =
    'Update transaction <b>→</b>';
}

window.editTransaction = editTransaction;


document.addEventListener("DOMContentLoaded", () => {
  $("loginForm").addEventListener("submit", login);

  $("logout").onclick = () => {
    localStorage.removeItem("expenseUser");
    location.reload();
  };

  $("expenseBtn").onclick = () => openModal("EXPENSE");
  $("incomeBtn").onclick = () => openModal("INCOME");
  $("newTransaction").onclick = () => openModal("EXPENSE");

  $("closeModal").onclick = closeModal;
  $("closeX").onclick = closeModal;
  $("chooseExpense").onclick = () => setType("EXPENSE");
  $("chooseIncome").onclick = () => setType("INCOME");

  $("transactionForm").addEventListener("submit", saveTransaction);
  $("search").addEventListener("input", renderTransactions);
  $("typeFilter").addEventListener("change", renderTransactions);
  $("sortFilter")?.addEventListener("change", renderTransactions);

  document.querySelectorAll("[data-page]").forEach(button => {
    button.addEventListener("click", () => {
      const page = button.dataset.page;

      document.querySelectorAll(".nav").forEach(n =>
        n.classList.toggle("active", n.dataset.page === page)
      );

      ["overview","transactions","analytics"].forEach(name => {
        $(`${name}Page`).classList.toggle("hidden", name !== page);
      });
    });
  });

  if (state.user) showApp();
});
