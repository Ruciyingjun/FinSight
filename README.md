<!-- 项目说明：记录金融研报 RAG 系统的目标、当前进度、运行方式和 HTTP 接口。 -->

# Financial Research RAG

面向金融与保险资料的检索增强生成（RAG）服务平台。
<img width="2736" height="1976" alt="236b5bddac29b6a32d4ca94f2f6d610b" src="https://github.com/user-attachments/assets/3547ec41-d0c7-4939-82ca-61dcf56ccf06" />


## 技术栈一览

- Java 21、Spring Boot 3.5、Spring MVC、Jakarta Bean Validation
- Spring Data JPA、Hibernate、PostgreSQL、Flyway
- Apache PDFBox、Apache POI、Tesseract OCR
- BM25、向量检索、RRF 混合召回、动态重排序、上下文组装、OpenAI 兼容大模型接口
- Maven、JUnit 5、Spring Boot Test、H2 测试数据库

## 当前进度

- [x] Spring Boot 基础工程
- [x] 系统状态接口与基础测试
- [x] 文档上传与生命周期管理
- [x] 可搜索 PDF 的逐页文本解析
- [x] Office 与纯文本解析
- [x] 可配置的扫描 PDF OCR 回退
- [x] 带页码和原文位置的边界感知切块
- [x] 数据库 BM25 关键词检索
- [x] 本地向量检索与混合召回
- [x] 重排序、动态阈值与上下文组装
- [x] 抽取式问答编排与原文引用
- [x] 可配置的大模型答案生成与安全回退
- [x] 每次问答明确标识大模型生成或本地回退
- [x] 对话式前端与文档资料库界面

## 已实现功能总览

### 工程基础

- 使用 Java 21、Spring Boot 3.5.7 和 Maven 构建后端服务。
- 提供统一的应用配置、类型安全配置绑定和系统状态接口。
- 提供 Actuator 健康检查端点。
- 使用统一 JSON 错误结构返回错误编码、错误信息、时间和字段校验详情。
- 使用 Jakarta Bean Validation 校验 HTTP 请求参数。

### 数据与文件存储

- 使用 Spring Data JPA 管理文档、页面和文本块数据。
- 运行环境默认使用 PostgreSQL，测试环境使用内存 H2 数据库。
- 使用 Flyway 管理数据库版本，目前包含文档、页面和文本块三版迁移。
- 原始文档默认保存在本地文件目录，存储层通过接口隔离，可替换为 MinIO 等对象存储。
- 上传文件生成独立存储名称，避免重名覆盖。
- 为上传文件计算 SHA-256 摘要。
- 数据库写入失败时清理已保存文件，删除文档时同步删除原始文件和关联数据。

### 文档管理

- 支持上传、列表、详情和删除文档。
- 当前允许 PDF、Word、Excel、PowerPoint、TXT、Markdown 和 CSV 扩展名。
- 已实现空文件、空文件名、超长文件名和不支持类型校验。
- 会移除上传文件名中的目录部分，防止路径穿越。
- 默认限制单个上传文件和请求大小为 50 MB。
- 文档具有完整处理状态：

  ```text
  UPLOADED → QUEUED → PARSING → CHUNKING → INDEXING → READY
                     ↘ FAILED
  ```

- 文档处理使用有界后台线程池，处理接口立即返回 `202 Accepted`，不会让上传请求等待解析完成。
- 前端自动轮询并展示“排队中、解析中、切块中、索引中、已就绪”状态，完成或失败时给出提示。
- 非法状态转换返回冲突错误，失败文档可以重新触发处理；处理中禁止重复提交和删除。
- 服务重启后会自动重新调度处于未完成状态的文档，不会永久停留在处理中。
- 默认同时处理 2 份文档、最多等待 20 份，可通过 `DOCUMENT_PROCESSING_WORKERS` 和 `DOCUMENT_PROCESSING_QUEUE_CAPACITY` 调整。

### 流式问答

- 前端通过 `POST /api/v1/questions/stream` 建立 SSE 流式连接，DeepSeek 生成的文本会逐段显示。
- 检索完成后先返回引用资料，生成结束后再返回经过引用合法性校验的最终答案。
- 如果模型调用失败或生成了无效引用，最终事件会把临时内容替换为本地抽取式答案，并显示“本地回退”。
- 原有 `POST /api/v1/questions` 非流式接口继续保留，便于脚本调用和兼容已有客户端。

### Agentic 查询路由与证据门控

- DeepSeek 首先作为查询规划 Agent，根据问题动态选择“直接回答、检索资料、请求澄清”，不维护固定问候词集合。
- 问候、致谢、闲聊和系统能力问题通常由 Agent 直接回答，不再执行无意义的文档检索，答案来源显示为“智能路由”。
- 信息不完整的问题由 Agent 生成针对当前问题的澄清问题，而不是返回固定话术。
- 需要资料的问题由 Agent 生成或改写检索词，并进一步选择 `FACT_SEARCH`（局部事实）或 `DOCUMENT_SUMMARY`（全文总结）策略。
- 查询规划 Agent 会同时接收当前资料范围和真实文件名；选中单份文档后，“这份文档”“该报告”等指代会解析为当前文档，Agent 还能根据财报、简历等文档类型扩展检索主题。
- 普通事实检索继续执行严格的关键词覆盖率证据门控；全文总结采用主题候选加首、中、尾代表性分块，并优先选择不同页面，避免“核心内容”这类抽象问法因没有字面重合而清空证据，或只把同一页的局部内容传给大模型。
- 最终生成提示会携带当前文档名称、原始问题和已编号证据，降低切换文档后模型混淆资料范围的概率。
- 文档证据必须同时通过相对分数、绝对分数和有效查询词覆盖率门槛；没有合格证据时明确说明未找到相关内容。
- 可通过 `RETRIEVAL_ABSOLUTE_THRESHOLD` 和 `RETRIEVAL_MINIMUM_QUERY_COVERAGE` 调整证据门槛。

当前采用受控的 Agent 动作流程：

```text
原始问题 + 当前资料范围 → DeepSeek 查询规划 Agent
  ├─ DIRECT_ANSWER → Agent 直接生成回复，不查询文档
  ├─ CLARIFY → Agent 生成澄清问题，不查询文档
  └─ RETRIEVE → 使用 Agent 改写的检索词
       ├─ FACT_SEARCH → 混合检索 → 严格重排序与证据门控
       └─ DOCUMENT_SUMMARY → 混合检索 → 跨页多样化证据选择
                                      ↓
                              携带文档名与证据生成回答
```

重排序分数由查询词覆盖率、原始检索相对分数和匹配密度组成：

```text
最终分数 = 查询词覆盖率 × 0.65 + 原始检索相对分数 × 0.25 + 匹配密度 × 0.10
```

中文门控优先使用双字词，减少“的、是、经”等单字重合造成的假相关。候选证据必须同时满足：

- 不低于本次最高分乘以相对阈值，默认 `0.35`；
- 不低于绝对阈值，默认 `0.40`；
- 有效查询词覆盖率不低于 `0.20`。

上述阈值只用于 `FACT_SEARCH`。`DOCUMENT_SUMMARY` 会在混合检索候选中优先选择不同页面，再用同页其他候选补足数量，从而兼顾主题相关性和全文覆盖。查询规划调用失败或未启用大模型时，会安全降级到原问题的事实检索。

### PDF 解析

- 使用 Apache PDFBox 解析可搜索 PDF。
- 按页提取文本并保留原始页码。
- 统一换行格式并清理行尾空白。
- 保存每页文本和字符数量。
- 损坏、加密或无法读取的 PDF 会进入 `FAILED` 状态并记录失败原因。
- 使用统一 `DocumentParser` 接口，为后续 Office 和 OCR 解析器预留扩展点。

### Office 与纯文本解析

- 使用 Apache POI 5.5.1 提取 Word、Excel 和 PowerPoint 文档中的可搜索文本。
- 同时支持 Office Open XML 格式（DOCX、XLSX、PPTX）及旧版二进制格式（DOC、XLS、PPT）。
- 支持 UTF-8 编码的 TXT、Markdown 和 CSV 文件，并自动移除 UTF-8 BOM。
- 统一规范化换行、行尾空白和 Office 空行。
- Office 与纯文本当前作为单个逻辑页处理；PDF 继续保留真实页码。
- 加密、损坏、格式不匹配或没有可提取文本的 Office 文件会进入 `FAILED` 状态。

### 扫描 PDF OCR

- PDF 页面内嵌文本不足时，可以将该页渲染为图片并调用本地 Tesseract OCR。
- OCR 通过 `OcrEngine` 接口隔离，后续可以替换为云 OCR 或其他本地实现。
- 默认关闭 OCR，普通可搜索 PDF 不需要安装 Tesseract。
- 纯扫描 PDF 在 OCR 关闭时会返回明确提示，不会生成空页面并错误进入 `READY` 状态。
- OCR 临时图片和结果会在每页处理结束后自动清理。
- 支持中英文识别语言、渲染 DPI、超时时间和文本触发阈值配置。

安装 Tesseract 及中文语言包后，可以使用以下环境变量开启：

```bash
export OCR_ENABLED=true
export OCR_EXECUTABLE=tesseract
export OCR_LANGUAGES=chi_sim+eng
export OCR_DPI=300
export OCR_TIMEOUT_SECONDS=60
export OCR_MINIMUM_TEXT_CHARACTERS=20
```

启动前可运行 `tesseract --list-langs`，确认 `chi_sim` 和 `eng` 已安装。

### 文本切块

- 每个 PDF 页面独立切块，不会把不同页面错误合并。
- 默认最大块长度为 1000 字符，相邻块重叠 150 字符。
- 切块参数可以通过环境变量调整。
- 优先从段落、换行、句末标点和空格等自然边界断开。
- 每个文本块保留文档 ID、全局块序号、页码、页内起止位置、原文和字符数量。
- 重新处理文档时会替换旧页面和旧文本块。

### BM25 关键词检索

- 基于持久化文本块实现 BM25 词频、逆文档频率和文本长度归一化评分。
- 支持英文大小写归一化、数字词元及中英文混合查询。
- 中文文本同时生成单字和双字词元。
- 支持全资料库检索和限定单个文档检索。
- 支持返回 1 至 50 条 Top-K 结果。
- 检索结果按相关性分数排序，并携带页码和原文位置。
- 通过 `ChunkRetriever` 接口隔离具体实现，可继续接入 Elasticsearch、向量检索和混合召回。

### 向量与混合检索

- 文档处理时为每个文本块生成并持久化归一化向量。
- 当前提供无需外部服务的本地哈希向量实现，方便离线开发和完整链路测试。
- 支持 `KEYWORD`、`VECTOR` 和 `HYBRID` 三种检索模式。
- 混合模式使用 Reciprocal Rank Fusion 融合 BM25 和向量检索排名。
- Embedding 通过接口隔离，后续可以替换为真实模型并迁移到 PostgreSQL `pgvector` 索引。

### 问答与引用

- 提供统一问答接口，自动完成检索、引用编号和答案组装。
- 问答会扩大初始候选集，并综合原始召回分数、查询词覆盖率和文本密度重新排序。
- 使用相对最高分的动态阈值过滤弱相关证据，减少无关引用。
- 自动合并同页相邻文本块并移除重叠内容，同时限制上下文总长度。
- 默认使用确定性的抽取式答案生成器，不依赖外部大模型服务。
- 可以启用 OpenAI `chat/completions` 兼容接口生成更自然的中文答案，也可连接实现相同协议的其他模型服务。
- 大模型提示词要求只能使用检索证据，并要求每个事实附带 `[n]` 引用。
- 调用 DeepSeek 时显式关闭思考模式，避免有限的输出 token 被推理过程耗尽而返回空正文。
- 服务会验证模型答案中的引用编号；调用失败、答案无引用或引用越界时自动回退到抽取式答案。
- 问答接口通过 `answerSource` 返回实际答案来源，取值为 `LARGE_LANGUAGE_MODEL` 或 `LOCAL_FALLBACK`。
- 答案使用 `[1]`、`[2]` 等编号关联引用。
- 每条引用包含文档 ID、页码、文本块序号、页内位置、原文和 BM25 分数。
- 未检索到证据时返回明确提示，不生成无依据内容。
- 通过 `AnswerGenerator` 和 `LlmClient` 接口隔离答案生成方式及具体模型供应商。

项目默认适配 DeepSeek 的 OpenAI 兼容接口。启用大模型生成：

```bash
export LLM_ENABLED=true
export DEEPSEEK_API_KEY='请填写新生成的 API Key'
export LLM_ENDPOINT='https://api.deepseek.com/chat/completions'
export LLM_MODEL='deepseek-flash'
export LLM_TIMEOUT_SECONDS=60
export LLM_MAX_TOKENS=800
export LLM_TEMPERATURE=0.1
```

也可以使用通用的 `LLM_API_KEY`，其优先级高于 `DEEPSEEK_API_KEY`。密钥只通过运行环境提供，不要写入源码或提交到版本控制。使用其他兼容服务时，只需更换接口地址、密钥和模型名称。

### 测试与可靠性

- 包含应用上下文、系统状态、文档上传、文件校验、路径安全、PDF 解析、状态流转、页面查询、文本切块、BM25 检索、问答引用和参数校验测试。
- 测试会动态生成真实 PDF，验证逐页解析链路。
- 切块单元测试覆盖自然边界、文本重叠和跨页隔离。
- 测试覆盖检索重排序、动态过滤、上下文重叠合并、真实 DOCX、UTF-8 文本、扫描 PDF OCR 回退以及大模型安全降级。

### Web 前端

- 提供与 Spring Boot 一起发布的原生 HTML、CSS 和 JavaScript 前端，无需额外安装 Node.js。
- 包含左侧对话导航、中间智能问答区以及右侧文档和引用面板。
- 支持上传并自动处理文档、选择单一文档或全部资料作为问答范围。
- 在右侧文档树中双击用户上传的文档，可以打开或下载对应原文件。
- 提供独立资料库视图，可以查看处理状态、重试失败文档和删除资料。
- 问答结果展示引用标签，点击标签可以定位右侧对应原文。
- 双击右侧 PDF 引用卡片会直接打开原始排版的 PDF、跳转至引用页，并以标准 PDF 批注高亮对应原文；其他格式使用文本定位页兼容展示。
- 每条 AI 回答旁显示来源徽标，绿色“大模型生成”与黄色“本地回退”可直接区分实际生成路径。
- 最近会话保存在浏览器本地存储中；同一会话内的多次提问归入一个条目，点击可恢复完整问答，只有“新建对话”才会创建新条目。
- 长对话仅在消息区域内部滚动，输入框始终固定在问答区域底部。
- 提供桌面端和移动端响应式布局。

## 环境

- Java 21
- Maven 3.9+
- PostgreSQL 16+（运行服务时需要）

## PostgreSQL 配置

服务默认连接本机的 `financial_research` 数据库。请先在 PostgreSQL 中创建应用账号和数据库：

```sql
CREATE USER financial_research WITH PASSWORD '请替换为安全密码';
CREATE DATABASE financial_research OWNER financial_research;
```

随后通过环境变量提供连接信息：

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/financial_research'
export DATABASE_USERNAME='financial_research'
export DATABASE_PASSWORD='请替换为安全密码'
```

启动时 Flyway 会自动执行数据库迁移，创建 `documents`、`document_pages` 和 `document_chunks` 表。请不要手动创建这三张表。

后续实现向量检索时，建议在这套 PostgreSQL 上安装 `pgvector` 扩展；当前版本尚未依赖该扩展。

## 启动

```bash
mvn spring-boot:run
```

服务启动后，可访问：

```text
前端界面：http://localhost:8080/
GET http://localhost:8080/api/v1/system/status
GET http://localhost:8080/actuator/health
```

上传文档：

```bash
curl -F "file=@report.pdf" http://localhost:8080/api/v1/documents
```

文档接口：

```text
POST   /api/v1/documents       上传文档
GET    /api/v1/documents       获取文档列表
GET    /api/v1/documents/{id}  获取文档详情
DELETE /api/v1/documents/{id}  删除文档
POST   /api/v1/documents/{id}/process  异步提交解析任务（202 Accepted）
GET    /api/v1/documents/{id}/pages    获取逐页文本
GET    /api/v1/documents/{id}/chunks   获取有序文本块
GET    /api/v1/documents/{id}/file     内联打开或下载原始文档
GET    /api/v1/search?query=...&mode=HYBRID  关键词、向量或混合检索
POST   /api/v1/questions                带原文引用的问答
POST   /api/v1/questions/stream         SSE 流式问答
```

问答请求示例：

```json
{
  "question": "保险条款包含哪些保障？",
  "documentId": "可选的文档 ID",
  "limit": 5
}
```

## 测试

```bash
mvn test
```
