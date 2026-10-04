/** 前端逻辑：连接文档、处理和问答接口，并维护对话、引用及资料库交互状态。 */
(() => {
  "use strict";

  const state = {
    documents: [],
    selectedDocumentId: null,
    citations: [],
    messages: [],
    currentConversationId: null,
    documentsExpanded: true,
    sending: false,
    documentPollTimer: null
  };

  const HISTORY_KEY = "financialResearchConversations";

  const elements = Object.fromEntries([
    "historyNewChatButton", "historyList", "serviceState", "databaseBadge",
    "pageTitle", "pageSubtitle", "chatView", "datasetView", "messageList", "scopeBanner",
    "questionForm", "questionInput", "sendButton", "documentPanel", "mobileDocumentsButton",
    "closeDocumentsButton", "documentCount", "documentSearch", "uploadButton", "datasetUploadButton",
    "fileInput", "allDocumentsButton", "allDocumentsMeta", "documentsBranch", "documentList", "citationCount", "citationList",
    "datasetTableBody", "datasetEmpty", "toast"
  ].map(id => [id, document.getElementById(id)]));

  document.addEventListener("DOMContentLoaded", async () => {
    try {
      await initialize();
    } catch (error) {
      console.error("前端初始化失败", error);
      if (elements.databaseBadge) {
        elements.databaseBadge.className = "status-pill offline";
        elements.databaseBadge.textContent = "前端初始化失败";
      }
      if (elements.serviceState) elements.serviceState.textContent = "界面加载失败";
      showToast(`界面加载失败：${error.message || "请刷新页面重试"}`);
    }
  });

  async function initialize() {
    bindEvents();
    renderWelcome();
    renderHistory();
    await Promise.all([checkService(), loadDocuments()]);
  }

  function bindEvents() {
    elements.historyNewChatButton?.addEventListener("click", resetConversation);
    elements.questionForm.addEventListener("submit", submitQuestion);
    elements.questionInput.addEventListener("keydown", event => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        elements.questionForm.requestSubmit();
      }
    });
    elements.questionInput.addEventListener("input", autoResizeComposer);
    elements.uploadButton.addEventListener("click", () => elements.fileInput.click());
    elements.datasetUploadButton.addEventListener("click", () => elements.fileInput.click());
    elements.fileInput.addEventListener("change", uploadSelectedFile);
    elements.documentSearch.addEventListener("input", renderDocuments);
    elements.allDocumentsButton.addEventListener("click", toggleAllDocuments);
    elements.mobileDocumentsButton.addEventListener("click", () => elements.documentPanel.classList.add("open"));
    elements.closeDocumentsButton.addEventListener("click", () => elements.documentPanel.classList.remove("open"));
    document.querySelectorAll(".nav-item").forEach(button => button.addEventListener("click", () => switchView(button.dataset.view)));
  }

  async function checkService() {
    try {
      const response = await fetchJson("/api/v1/system/status");
      const online = response.status === "UP";
      elements.databaseBadge.className = `status-pill ${online ? "online" : "offline"}`;
      elements.databaseBadge.lastChild.textContent = online ? " 服务已连接" : " 服务异常";
      elements.serviceState.textContent = online ? "系统在线" : "系统异常";
    } catch (error) {
      elements.databaseBadge.className = "status-pill offline";
      elements.databaseBadge.lastChild.textContent = " 服务不可用";
      elements.serviceState.textContent = "连接失败";
    }
  }

  async function loadDocuments() {
    try {
      const previousStatuses = new Map(state.documents.map(doc => [doc.id, doc.status]));
      state.documents = await fetchJson("/api/v1/documents");
      if (state.selectedDocumentId && !state.documents.some(doc => doc.id === state.selectedDocumentId)) {
        state.selectedDocumentId = null;
      }
      renderDocuments();
      renderDataset();
      updateScope();
      state.documents.forEach(doc => {
        const previous = previousStatuses.get(doc.id);
        if (previous && previous !== doc.status && doc.status === "READY") {
          showToast(`“${doc.filename}”处理完成，可以开始提问`);
        } else if (previous && previous !== doc.status && doc.status === "FAILED") {
          showToast(`“${doc.filename}”处理失败：${doc.failureReason || "未知错误"}`);
        }
      });
      scheduleDocumentPolling();
    } catch (error) {
      showToast(error.message);
      scheduleDocumentPolling(true);
    }
  }

  function scheduleDocumentPolling(retryAfterError = false) {
    if (state.documentPollTimer) window.clearTimeout(state.documentPollTimer);
    const hasActiveDocument = state.documents.some(doc => isProcessing(doc.status));
    state.documentPollTimer = (hasActiveDocument || retryAfterError)
      ? window.setTimeout(loadDocuments, retryAfterError ? 3000 : 1200)
      : null;
  }

  function renderWelcome() {
    elements.messageList.replaceChildren();
    const wrapper = create("div", "welcome");
    wrapper.append(create("div", "welcome-icon", "⌁"), create("h2", "", "探索你的金融资料"),
      create("p", "", "上传研报、保险条款或合同，然后用自然语言提问。每个回答都会附上可核验的原文依据。"));
    const suggestions = create("div", "suggestions");
    ["这份资料的核心结论是什么？", "保险责任和免责条款有哪些？", "提取关键财务指标并总结趋势", "对比不同产品的保障范围"]
      .forEach(text => {
        const button = create("button", "suggestion", text);
        button.type = "button";
        button.addEventListener("click", () => { elements.questionInput.value = text; elements.questionInput.focus(); });
        suggestions.append(button);
      });
    wrapper.append(suggestions);
    elements.messageList.append(wrapper);
  }

  function renderDocuments() {
    const keyword = elements.documentSearch.value.trim().toLowerCase();
    const filtered = state.documents.filter(doc => doc.filename.toLowerCase().includes(keyword));
    elements.documentCount.textContent = `${state.documents.length} 份资料`;
    const readyCount = state.documents.filter(doc => doc.status === "READY").length;
    elements.allDocumentsMeta.textContent = `${state.documents.length} 份资料 · ${readyCount} 份可用于问答`;
    elements.documentList.replaceChildren();
    elements.allDocumentsButton.classList.toggle("selected", state.selectedDocumentId === null);
    elements.allDocumentsButton.setAttribute("aria-expanded", String(state.documentsExpanded));
    elements.documentsBranch.setAttribute("aria-hidden", String(!state.documentsExpanded));
    elements.documentsBranch.inert = !state.documentsExpanded;
    elements.documentsBranch.classList.toggle("collapsed", !state.documentsExpanded);
    filtered.forEach(doc => {
      const button = create("button", `document-card${state.selectedDocumentId === doc.id ? " selected" : ""}`);
      button.type = "button";
      const icon = create("span", "file-icon", fileType(doc.filename));
      const text = create("span");
      text.append(create("strong", "", doc.filename), create("small", "", `${statusText(doc.status)} · ${formatBytes(doc.sizeBytes)}`));
      const status = create("i", `doc-status ${doc.status.toLowerCase()}`);
      button.append(icon, text, status);
      if (doc.failureReason) button.title = `处理失败：${doc.failureReason}`;
      button.addEventListener("click", () => selectDocument(doc.id));
      button.addEventListener("dblclick", event => {
        event.preventDefault();
        openOriginalDocument(doc);
      });
      if (!doc.failureReason) button.title = "单击选择问答范围，双击打开原文件";
      elements.documentList.append(button);
    });
  }

  function renderDataset() {
    elements.datasetTableBody.replaceChildren();
    elements.datasetEmpty.classList.toggle("hidden", state.documents.length !== 0);
    state.documents.forEach(doc => {
      const row = document.createElement("tr");
      const filename = document.createElement("td");
      filename.textContent = doc.filename;
      const statusCell = document.createElement("td");
      statusCell.append(create("span", `status-tag ${doc.status.toLowerCase()}`, statusText(doc.status)));
      const size = create("td", "", formatBytes(doc.sizeBytes));
      const date = create("td", "", formatDate(doc.createdAt));
      const actions = document.createElement("td");
      if (doc.status === "UPLOADED" || doc.status === "FAILED") {
        const process = create("button", "table-action", doc.status === "FAILED" ? "重试" : "处理");
        process.type = "button";
        process.addEventListener("click", () => processDocument(doc));
        actions.append(process);
      }
      const remove = create("button", "table-action danger", "删除");
      remove.type = "button";
      remove.addEventListener("click", () => deleteDocument(doc));
      actions.append(remove);
      row.append(filename, statusCell, size, date, actions);
      elements.datasetTableBody.append(row);
    });
  }

  function selectDocument(id) {
    state.selectedDocumentId = id;
    renderDocuments();
    updateScope();
    elements.documentPanel.classList.remove("open");
  }

  function toggleAllDocuments() {
    state.documentsExpanded = !state.documentsExpanded;
    selectDocument(null);
  }

  function openOriginalDocument(doc) {
    const url = `/api/v1/documents/${encodeURIComponent(doc.id)}/file`;
    const opened = window.open(url, "_blank", "noopener,noreferrer");
    if (!opened) showToast("浏览器阻止了新窗口，请允许弹出窗口后重试");
  }

  function updateScope() {
    if (!state.selectedDocumentId) {
      const ready = state.documents.filter(doc => doc.status === "READY").length;
      elements.scopeBanner.textContent = `正在使用全部已就绪文档（${ready}）`;
      return;
    }
    const selected = state.documents.find(doc => doc.id === state.selectedDocumentId);
    elements.scopeBanner.textContent = selected ? `当前文档：${selected.filename}` : "正在使用全部已就绪文档";
  }

  async function uploadSelectedFile() {
    const file = elements.fileInput.files[0];
    elements.fileInput.value = "";
    if (!file) return;
    const data = new FormData();
    data.append("file", file);
    showToast(`正在上传 ${file.name}…`);
    try {
      const uploaded = await fetchJson("/api/v1/documents", { method: "POST", body: data });
      state.selectedDocumentId = uploaded.id;
      await processDocument(uploaded, false);
      showToast("上传成功，已加入后台处理队列");
    } catch (error) {
      showToast(error.message);
      await loadDocuments();
    }
  }

  async function processDocument(doc, reload = true) {
    try {
      await fetchJson(`/api/v1/documents/${encodeURIComponent(doc.id)}/process`, { method: "POST" });
      await loadDocuments();
      if (reload) showToast("已加入后台处理队列");
    } catch (error) {
      showToast(error.message);
      await loadDocuments();
      throw error;
    }
  }

  async function deleteDocument(doc) {
    if (!window.confirm(`确定删除“${doc.filename}”吗？`)) return;
    try {
      const response = await fetch(`/api/v1/documents/${encodeURIComponent(doc.id)}`, { method: "DELETE" });
      if (!response.ok) throw new Error("删除文档失败");
      if (state.selectedDocumentId === doc.id) state.selectedDocumentId = null;
      await loadDocuments();
      showToast("文档已删除");
    } catch (error) { showToast(error.message); }
  }

  async function submitQuestion(event) {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!question || state.sending) return;
    if (!state.documents.some(doc => doc.status === "READY" && (!state.selectedDocumentId || doc.id === state.selectedDocumentId))) {
      showToast("请先上传并处理至少一份文档");
      return;
    }
    if (elements.messageList.querySelector(".welcome")) elements.messageList.replaceChildren();
    appendUserMessage(question);
    const loading = appendAssistantLoading();
    elements.questionInput.value = "";
    autoResizeComposer();
    setSending(true);
    try {
      const body = { question, limit: 5 };
      if (state.selectedDocumentId) body.documentId = state.selectedDocumentId;
      let streamedText = "";
      const response = await streamQuestion(body, (event, data) => {
        if (event === "context") {
          renderCitations(data.citations || []);
        } else if (event === "token") {
          loading.querySelector(".answer").classList.remove("loading");
          streamedText += data.text || "";
          loading.querySelector(".answer").textContent = streamedText;
          scrollMessages();
        }
      });
      finalizeAssistantMessage(loading, response.answer, response.citations || [], response.answerSource);
      state.messages.push({
        question,
        answer: response.answer,
        answerSource: response.answerSource,
        citations: response.citations || []
      });
      saveConversation();
      renderCitations(response.citations || []);
    } catch (error) {
      loading.querySelector(".answer").classList.remove("loading");
      loading.querySelector(".answer").textContent = `请求失败：${error.message}`;
    } finally { setSending(false); }
  }

  function appendUserMessage(text) {
    const message = create("div", "message user");
    message.append(create("div", "bubble", text));
    elements.messageList.append(message);
    scrollMessages();
  }

  function appendAssistantLoading() {
    const message = assistantMessageShell();
    message.querySelector(".answer").classList.add("loading");
    message.querySelector(".answer").textContent = "正在检索资料并组织答案…";
    elements.messageList.append(message);
    scrollMessages();
    return message;
  }

  function appendAssistantMessage(answer, citations, answerSource) {
    const message = assistantMessageShell();
    finalizeAssistantMessage(message, answer, citations, answerSource);
    elements.messageList.append(message);
    scrollMessages();
  }

  function finalizeAssistantMessage(message, answer, citations, answerSource) {
    message.querySelector(".answer").classList.remove("loading");
    message.querySelector(".answer").textContent = answer;
    message.querySelectorAll(".answer-source, .inline-citations").forEach(element => element.remove());
    const source = answerSource === "LARGE_LANGUAGE_MODEL"
      ? { className: "model", text: "大模型生成", title: "本次答案由配置的大语言模型生成" }
      : answerSource === "AGENT_RESPONSE"
        ? { className: "direct", text: "智能路由", title: "Agent 判断该问题无需检索资料并直接生成回复" }
        : { className: "fallback", text: "本地回退", title: "大模型未启用、调用失败或答案引用校验未通过，本次使用本地抽取式答案" };
    const badge = create("span", `answer-source ${source.className}`, source.text);
    badge.title = source.title;
    message.querySelector(".label").append(badge);
    const chips = create("div", "inline-citations");
    citations.forEach(citation => {
      const doc = state.documents.find(item => item.id === citation.documentId);
      const chip = create("button", "citation-chip", `[${citation.number}] ${doc ? doc.filename : "原文"} · P${citation.pageNumber}`);
      chip.type = "button";
      chip.addEventListener("click", () => focusCitation(citation.number));
      chips.append(chip);
    });
    message.querySelector(".assistant-content").append(chips);
    scrollMessages();
  }

  async function streamQuestion(body, onEvent) {
    const response = await fetch("/api/v1/questions/stream", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "text/event-stream, application/json" },
      body: JSON.stringify(body)
    });
    if (!response.ok) {
      let errorBody = null;
      try { errorBody = await response.json(); } catch (ignored) { /* 响应可能不是 JSON。 */ }
      throw new Error((errorBody && errorBody.message) || `请求失败（${response.status}）`);
    }
    if (!response.body) throw new Error("当前浏览器不支持流式响应");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    let completed = null;
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done }).replace(/\r\n/g, "\n");
      let boundary;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const parsed = parseSseBlock(block);
        if (!parsed) continue;
        if (parsed.event === "error") throw new Error(parsed.data.message || "流式问答失败");
        if (parsed.event === "complete") completed = parsed.data;
        onEvent(parsed.event, parsed.data);
      }
      if (done) break;
    }
    if (!completed) throw new Error("流式连接提前结束，未收到完整答案");
    return completed;
  }

  function parseSseBlock(block) {
    let event = "message";
    const dataLines = [];
    block.split("\n").forEach(line => {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
    });
    if (!dataLines.length) return null;
    try {
      return { event, data: JSON.parse(dataLines.join("\n")) };
    } catch (error) {
      throw new Error("无法解析服务器流式响应");
    }
  }

  function assistantMessageShell() {
    const message = create("div", "message assistant-row");
    const content = create("div", "assistant-content");
    content.append(create("div", "label", "FinSight AI"), create("div", "answer"));
    message.append(create("div", "assistant-icon", "F"), content);
    return message;
  }

  function renderCitations(citations) {
    state.citations = citations;
    elements.citationCount.textContent = String(citations.length);
    elements.citationList.replaceChildren();
    if (!citations.length) {
      elements.citationList.append(create("p", "citation-empty", "本次回答没有找到可引用的原文依据。"));
      return;
    }
    citations.forEach(citation => {
      const card = create("article", "citation-card");
      card.dataset.citation = citation.number;
      const doc = state.documents.find(item => item.id === citation.documentId);
      const header = document.createElement("header");
      header.append(create("span", "", `[${citation.number}] ${doc ? doc.filename : "资料原文"}`), create("span", "", `第 ${citation.pageNumber} 页`));
      card.append(header, create("p", "", citation.quote));
      card.title = "双击定位并高亮原文";
      card.tabIndex = 0;
      card.addEventListener("dblclick", () => openCitationSource(citation, doc));
      elements.citationList.append(card);
    });
  }

  function openCitationSource(citation, sourceDocument) {
    const parameters = new URLSearchParams({
      documentId: citation.documentId,
      page: String(citation.pageNumber),
      start: String(citation.startOffset),
      end: String(citation.endOffset),
      citation: String(citation.number)
    });
    const isPdf = sourceDocument && sourceDocument.filename.toLowerCase().endsWith(".pdf");
    const target = isPdf
      ? `/api/v1/documents/${encodeURIComponent(citation.documentId)}/file/highlight?${new URLSearchParams({
          page: String(citation.pageNumber),
          start: String(citation.startOffset),
          end: String(citation.endOffset)
        })}#page=${citation.pageNumber}`
      : `/citation-viewer.html?${parameters}`;
    const opened = window.open(target, "_blank", "noopener,noreferrer");
    if (!opened) showToast("浏览器阻止了新窗口，请允许弹出窗口后重试");
  }

  function focusCitation(number) {
    if (window.innerWidth <= 1050) elements.documentPanel.classList.add("open");
    const card = elements.citationList.querySelector(`[data-citation="${number}"]`);
    if (card) { card.scrollIntoView({ behavior: "smooth", block: "center" }); card.animate([{ background: "#eaf0ff" }, { background: "#fff" }], 900); }
  }

  function switchView(view) {
    const dataset = view === "dataset";
    elements.chatView.classList.toggle("hidden", dataset);
    elements.datasetView.classList.toggle("hidden", !dataset);
    document.querySelectorAll(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.view === view));
    elements.pageTitle.textContent = dataset ? "文档资料库" : "金融资料智能问答";
    elements.pageSubtitle.textContent = dataset ? "管理用于检索和问答的知识来源" : "基于已上传资料回答，并提供可追溯的原文引用";
  }

  function resetConversation() {
    state.messages = [];
    state.citations = [];
    state.currentConversationId = null;
    renderWelcome();
    renderCitations([]);
    switchView("chat");
  }

  function saveConversation() {
    if (!state.messages.length) return;
    const history = readHistory();
    const now = Date.now();
    if (!state.currentConversationId) {
      state.currentConversationId = window.crypto && crypto.randomUUID
        ? crypto.randomUUID()
        : `conversation-${now}`;
    }
    const conversation = {
      id: state.currentConversationId,
      title: state.messages[0].question,
      updatedAt: now,
      messages: state.messages
    };
    const updated = [conversation, ...history.filter(item => item.id !== conversation.id)]
      .sort((left, right) => right.updatedAt - left.updatedAt)
      .slice(0, 12);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(updated));
    renderHistory();
  }

  function renderHistory() {
    const history = readHistory();
    elements.historyList.replaceChildren();
    history.forEach(item => {
      const entry = create("div", `history-item${item.id === state.currentConversationId ? " active" : ""}`);
      const openButton = create("button", "history-open", item.title);
      openButton.type = "button";
      openButton.title = item.title;
      openButton.addEventListener("click", () => openConversation(item));
      const deleteButton = create("button", "history-delete", "×");
      deleteButton.type = "button";
      deleteButton.title = `删除对话：${item.title}`;
      deleteButton.setAttribute("aria-label", `删除对话：${item.title}`);
      deleteButton.addEventListener("click", event => {
        event.stopPropagation();
        deleteConversation(item.id);
      });
      entry.append(openButton, deleteButton);
      elements.historyList.append(entry);
    });
  }

  function readHistory() {
    try {
      const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
      return Array.isArray(history)
        ? history.filter(item => item && item.id && item.title && Array.isArray(item.messages))
        : [];
    } catch {
      return [];
    }
  }

  function openConversation(conversation) {
    state.currentConversationId = conversation.id;
    state.messages = conversation.messages;
    elements.messageList.replaceChildren();
    state.messages.forEach(message => {
      appendUserMessage(message.question);
      appendAssistantMessage(message.answer, message.citations || [], message.answerSource);
    });
    const latest = state.messages[state.messages.length - 1];
    renderCitations(latest ? latest.citations || [] : []);
    renderHistory();
    switchView("chat");
  }

  function deleteConversation(conversationId) {
    const remaining = readHistory().filter(item => item.id !== conversationId);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(remaining));
    if (state.currentConversationId === conversationId) {
      resetConversation();
      return;
    }
    renderHistory();
  }
  function setSending(value) { state.sending = value; elements.sendButton.disabled = value; }
  function autoResizeComposer() { elements.questionInput.style.height = "auto"; elements.questionInput.style.height = `${elements.questionInput.scrollHeight}px`; }
  function scrollMessages() { elements.messageList.scrollTop = elements.messageList.scrollHeight; }

  async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    let body = null;
    if (text) { try { body = JSON.parse(text); } catch { body = text; } }
    if (!response.ok) {
      const details = body && body.details ? Object.values(body.details).join("；") : null;
      throw new Error(details || (body && body.message) || `请求失败（${response.status}）`);
    }
    return body;
  }

  function create(tag, className = "", text = "") {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== "") node.textContent = text;
    return node;
  }
  function isProcessing(status) { return ["QUEUED", "PARSING", "CHUNKING", "INDEXING"].includes(status); }
  function statusText(status) { return ({ UPLOADED: "待处理", QUEUED: "排队中", PARSING: "解析中", CHUNKING: "切块中", INDEXING: "索引中", READY: "已就绪", FAILED: "处理失败" })[status] || status; }
  function fileType(filename) { const extension = filename.split(".").pop().toUpperCase(); return extension.length <= 4 ? extension : "FILE"; }
  function formatBytes(bytes) { if (bytes < 1024) return `${bytes} B`; if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`; return `${(bytes / 1048576).toFixed(1)} MB`; }
  function formatDate(value) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
  let toastTimer;
  function showToast(message) { clearTimeout(toastTimer); elements.toast.textContent = message; elements.toast.classList.add("show"); toastTimer = setTimeout(() => elements.toast.classList.remove("show"), 3200); }
})();
