const paperSample = `upper=0.23
lower=0.10

external:
A=2 B=5 C=1 D=3 E=6 F=4

batch DB0
T1: A:2 B:1 C:3
T2: C:1 D:2
T3: A:6 B:1 C:4 E:5
T4: A:4 B:4 E:3
T5: B:1 E:2 F:2

batch DB1
T6: A:3 F:3
T7: C:2 E:2

batch DB2
T8: B:5 C:1 D:3 E:2
T9: A:1 B:3 D:1`;

let inputText = paperSample;
let sourceName = "paper-example.txt";
let currentReport = null;
let analyzedInput = null;
let selectedBatch = 0;
let patternFilter = "ALL";
const $ = id => document.getElementById(id);
const format = (n, digits = 3) => Number(n).toLocaleString("vi-VN", { maximumFractionDigits: digits, minimumFractionDigits: digits });
const percentDelta = (oldValue, newValue) => oldValue === 0 ? "—" : `${format((newValue - oldValue) / oldValue * 100, 1)}%`;

$("chooseFileButton").addEventListener("click", () => $("fileInput").click());

$("sampleButton").addEventListener("click", () => {
  inputText = paperSample;
  sourceName = "paper-example.txt";
  $("fileInput").value = "";
  $("fileName").textContent = "paper-example.txt";
  $("su").value = "0.23"; $("sl").value = "0.10";
  invalidateReport();
  setStatus("idle", "Đã nạp dữ liệu bài báo");
});

$("fileInput").addEventListener("change", async event => {
  const file = event.target.files[0];
  if (!file) return;
  if (file.size > 20 * 1024 * 1024) return setStatus("error", "File vượt quá giới hạn 20 MB.");
  inputText = await file.text();
  sourceName = file.name;
  $("fileName").textContent = file.name;
  invalidateReport();
  setStatus("idle", `Đã đọc ${file.name}`);
});

$("runButton").addEventListener("click", run);
$("exportTxtButton").addEventListener("click", () => exportReport("txt"));
$("exportCsvButton").addEventListener("click", () => exportReport("csv"));
$("su").addEventListener("input", invalidateReport);
$("sl").addEventListener("input", invalidateReport);
document.querySelectorAll(".filter").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll(".filter").forEach(x => x.classList.remove("active"));
  button.classList.add("active"); patternFilter = button.dataset.filter; renderPatterns();
}));

async function run() {
  const su = Number($("su").value), sl = Number($("sl").value);
  if (!(sl > 0 && sl < su && su <= 1)) return setStatus("error", "Cần 0 < Sl < Su ≤ 1");
  invalidateReport();
  $("runButton").disabled = true; setStatus("running", "Đang chạy hai thuật toán…");
  try {
    const response = await fetch(`/api/analyze?su=${encodeURIComponent(su)}&sl=${encodeURIComponent(sl)}`, {method:"POST",headers:{"Content-Type":"text/plain;charset=utf-8"},body:inputText});
    if (!response.ok) throw new Error(await apiError(response));
    if (!response.headers.get("content-type")?.includes("application/json"))
      throw new Error("Server trả sai định dạng. Hãy đóng server cũ và chạy lại ứng dụng web.");
    const data = await response.json();
    currentReport = data; selectedBatch = data.tight.batches.length - 1;
    analyzedInput = { text: inputText, source: sourceName, su, sl };
    $("exportTxtButton").disabled = false;
    $("exportCsvButton").disabled = false;
    render(); setStatus("success", "Phân tích hoàn tất");
  } catch (error) { setStatus("error", error.message); }
  finally { $("runButton").disabled = false; }
}

function invalidateReport() {
  currentReport = null;
  analyzedInput = null;
  $("exportTxtButton").disabled = true;
  $("exportCsvButton").disabled = true;
  $("dashboard").hidden = true;
  $("emptyState").hidden = false;
}

async function exportReport(format) {
  if (!analyzedInput) return;
  const snapshot = analyzedInput;
  $("exportTxtButton").disabled = true;
  $("exportCsvButton").disabled = true;
  $("runButton").disabled = true;
  setStatus("running", `Đang tạo báo cáo ${format.toUpperCase()}…`);
  try {
    const query = new URLSearchParams({su: snapshot.su, sl: snapshot.sl, source: snapshot.source, format});
    const response = await fetch(`/api/export?${query}`, {
      method: "POST", headers: {"Content-Type": "text/plain;charset=utf-8"}, body: snapshot.text
    });
    if (!response.ok) throw new Error(await apiError(response));
    const expectedType = format === "txt" ? "text/plain" : "text/csv";
    if (!response.headers.get("content-type")?.includes(expectedType))
      throw new Error("Server chưa hỗ trợ xuất TXT/CSV. Hãy đóng server cũ và chạy lại ứng dụng web.");
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url; link.download = `PIHAUPA-bao-cao.${format}`;
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    setStatus("success", `Đã tải báo cáo ${format.toUpperCase()}`);
  } catch (error) { setStatus("error", error.message); }
  finally {
    $("runButton").disabled = false;
    $("exportTxtButton").disabled = !analyzedInput;
    $("exportCsvButton").disabled = !analyzedInput;
  }
}

async function apiError(response) {
  if (response.status === 404) return "Không tìm thấy chức năng trên server đang chạy (404). Hãy đóng server cũ và mở lại bằng run-web.ps1.";
  const body = await response.text();
  try { return JSON.parse(body).error || `Lỗi HTTP ${response.status}`; }
  catch { return `Lỗi HTTP ${response.status}: ${body.slice(0, 140) || "không có chi tiết"}`; }
}

function setStatus(kind, text) { const node=$("status"); node.dataset.kind=kind; node.querySelector("span").textContent=text; }
function metric(label, value, note) { return `<article class="metric panel"><small>${label}</small><strong>${value}</strong><em>${note}</em></article>`; }

function render() {
  $("emptyState").hidden = true; $("dashboard").hidden = false;
  const n=currentReport.tight, o=currentReport.original, last=n.batches.at(-1);
  $("summaryCards").innerHTML = metric("Giao dịch", n.transactionCount, `${n.batchCount} batch`) + metric("HAUP cuối", last.largePatterns.length, `${last.preLargePatterns.length} pre-large`) + metric("Thời gian tight", `${format(n.elapsedMs)} ms`, `${percentDelta(o.elapsedMs,n.elapsedMs)} so với cũ`) + metric("Heap peak", `${format(n.peakMemoryMb)} MB`, `tăng ${format(n.memoryDeltaMb)} MB`) + metric("Re-scan tight", n.stats.rescanCount, `cũ: ${o.stats.rescanCount} lần`);
  $("equivalence").textContent = currentReport.sameHaups ? "✓ HAUP trùng khớp" : "⚠ Kết quả khác nhau";
  const rows=[
    ["Số lần re-scan",o.stats.rescanCount,n.stats.rescanCount,n.stats.rescanCount-o.stats.rescanCount],
    ["Thời gian quan sát (ms)",format(o.elapsedMs),format(n.elapsedMs),percentDelta(o.elapsedMs,n.elapsedMs)],
    ["Heap peak quan sát (MB)",format(o.peakMemoryMb),format(n.peakMemoryMb),percentDelta(o.peakMemoryMb,n.peakMemoryMb)],
    ["Pattern đã duyệt",o.stats.patternsVisited,n.stats.patternsVisited,n.stats.patternsVisited-o.stats.patternsVisited],
    ["Node đã kết hợp",o.stats.combinedNodes,n.stats.combinedNodes,n.stats.combinedNodes-o.stats.combinedNodes]
  ];
  $("comparisonBody").innerHTML=rows.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td><td class="${String(r[3]).startsWith('-')?'delta-good':Number(r[3])>0?'delta-bad':''}">${r[3]}</td></tr>`).join("");
  $("batchTabs").innerHTML=n.batches.map((b,i)=>`<button class="batch-tab ${i===selectedBatch?'active':''}" data-index="${i}" role="tab">${b.name}</button>`).join("");
  $("batchTabs").querySelectorAll("button").forEach(btn=>btn.addEventListener("click",()=>{selectedBatch=Number(btn.dataset.index);renderBatches();}));
  renderBatches(); renderPatterns();
}

function renderBatches(){
  const n=currentReport.tight, b=n.batches[selectedBatch];
  $("batchTabs").querySelectorAll("button").forEach((x,i)=>x.classList.toggle("active",i===selectedBatch));
  const initial=b.tightRescanLimit==null;
  const oldTU=b.totalTransactionUtility-b.accumulatedTransactionUtility;
  const left=b.accumulatedMaximumUtility-n.upperThreshold*b.accumulatedTransactionUtility;
  const right=(n.upperThreshold-n.lowerThreshold)*oldTU;
  const decision=initial?"Quét DB0 và tạo PIHAUP-List/pattern tree lần đầu":`${format(left,6)} ${b.rescanTriggered?'≥':'<'} ${format(right,6)} → ${b.rescanTriggered?'RE-SCAN toàn bộ':'chỉ cập nhật pattern tree'}`;
  $("batchDetail").innerHTML=`<div class="batch-layout"><article class="batch-card"><h3>Dữ liệu & ngưỡng</h3><div class="stats-row"><div class="mini-stat"><span>Transaction</span><b>${b.transactionCount}</b></div><div class="mini-stat"><span>TU batch</span><b>${format(b.batchTransactionUtility)}</b></div><div class="mini-stat"><span>MU batch</span><b>${format(b.batchMaximumUtility)}</b></div><div class="mini-stat"><span>TU tích luỹ</span><b>${format(b.totalTransactionUtility)}</b></div><div class="mini-stat"><span>minUtil upper</span><b>${format(b.minUtilUpper)}</b></div><div class="mini-stat"><span>minUtil lower</span><b>${format(b.minUtilLower)}</b></div></div></article><article class="batch-card"><h3>Quyết định re-scan</h3><div class="decision ${b.rescanTriggered?'rescan':''}"><strong>${b.action}</strong><br>${decision}<br>Thời gian batch: ${format(b.elapsedMs)} ms · Memory: ${format(b.memoryAfterMb)} MB</div></article><article class="batch-card"><h3>LARGE / HAUP (${b.largePatterns.length})</h3><div class="pattern-chips">${chips(b.largePatterns,false)}</div></article><article class="batch-card"><h3>PRE-LARGE (${b.preLargePatterns.length})</h3><div class="pattern-chips">${chips(b.preLargePatterns,true)}</div></article></div>`;
}
function chips(items,pre){return items.length?items.map(p=>`<span class="chip ${pre?'pre':''}">${p.name} · ${format(p.averageUtility)}</span>`).join(""):"<span class='chip'>Không có mẫu</span>"}
function renderPatterns(){if(!currentReport)return;const b=currentReport.tight.batches.at(-1);let items=[...b.largePatterns,...b.preLargePatterns];if(patternFilter!=="ALL")items=items.filter(x=>x.type===patternFilter);$("patternBody").innerHTML=items.map(p=>`<tr><td><strong>${p.name}</strong></td><td><span class="type-badge ${p.type==='PRE_LARGE'?'pre':''}">${p.type.replace('_','-')}</span></td><td>${format(p.averageUtility)}</td><td>${format(p.sumUtility)}</td><td>${p.length}</td></tr>`).join("")}

$("sampleButton").click();
