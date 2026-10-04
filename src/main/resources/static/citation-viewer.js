/** 引用原文查看逻辑：读取目标文档页，按持久化字符偏移安全构建高亮并滚动定位。 */
(() => {
  "use strict";

  document.addEventListener("DOMContentLoaded", loadCitation);

  async function loadCitation() {
    const parameters = new URLSearchParams(window.location.search);
    const documentId = parameters.get("documentId");
    const pageNumber = positiveInteger(parameters.get("page"));
    const startOffset = nonNegativeInteger(parameters.get("start"));
    const endOffset = nonNegativeInteger(parameters.get("end"));
    const citationNumber = positiveInteger(parameters.get("citation"));
    const content = document.getElementById("pageContent");

    if (!documentId || !pageNumber || startOffset === null || endOffset === null || endOffset <= startOffset) {
      showError(content, "引用定位参数不完整，无法打开原文。");
      return;
    }

    try {
      const [documentInfo, page] = await Promise.all([
        fetchJson(`/api/v1/documents/${encodeURIComponent(documentId)}`),
        fetchJson(`/api/v1/documents/${encodeURIComponent(documentId)}/pages/${pageNumber}`)
      ]);
      document.title = `${documentInfo.filename} · 第 ${pageNumber} 页`;
      document.getElementById("documentName").textContent = documentInfo.filename;
      document.getElementById("pageMeta").textContent = `引用 [${citationNumber || "-"}] · 第 ${pageNumber} 页`;
      document.getElementById("pageLabel").textContent = `第 ${pageNumber} 页`;
      document.getElementById("originalFileLink").href = `/api/v1/documents/${encodeURIComponent(documentId)}/file#page=${pageNumber}`;
      renderHighlight(content, page.content || "", startOffset, endOffset);
    } catch (error) {
      showError(content, error.message);
    }
  }

  function renderHighlight(container, text, requestedStart, requestedEnd) {
    const start = Math.min(requestedStart, text.length);
    const end = Math.min(Math.max(requestedEnd, start), text.length);
    container.replaceChildren(document.createTextNode(text.slice(0, start)));
    const mark = document.createElement("mark");
    mark.id = "citation-highlight";
    mark.textContent = text.slice(start, end);
    container.append(mark, document.createTextNode(text.slice(end)));
    requestAnimationFrame(() => mark.scrollIntoView({ behavior: "smooth", block: "center" }));
  }

  async function fetchJson(url) {
    const response = await fetch(url);
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body && body.message ? body.message : `加载原文失败（${response.status}）`);
    return body;
  }

  function positiveInteger(value) {
    const number = Number.parseInt(value, 10);
    return Number.isInteger(number) && number > 0 ? number : null;
  }

  function nonNegativeInteger(value) {
    const number = Number.parseInt(value, 10);
    return Number.isInteger(number) && number >= 0 ? number : null;
  }

  function showError(container, message) {
    container.replaceChildren();
    const error = document.createElement("span");
    error.className = "error";
    error.textContent = message;
    container.append(error);
  }
})();
