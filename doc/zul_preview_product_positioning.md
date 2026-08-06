# ZUL Preview — 產品定位與建議

> 從產品角度評估 Layout Preview 功能。背景：ZKIdea 是一個**免費** plugin，
> 其全部使用者都是已經在使用（或正在評估）ZK Framework 的開發者。
>
> **狀態：本文寫於 0.8.0 開發期間；功能已隨 1.0.0 出貨。** 當初列為
> GA 前提的三項行動（格式化錯誤窗格、可行動的失敗狀態、綁定 placeholder 渲染）
> **全部已實作**，下文以 ✅ 標記。策略定位、成功指標與長期選項仍然有效。
> 事實面請以 [zul-preview-feature.md](zul-preview-feature.md)（使用者文件）與
> [zul_preview_spec.md](zul_preview_spec.md)（工程契約、限制、未修項目）為準。

---

## 1. 策略定位：這個功能是**為了什麼**

對一家商業 framework 廠商而言，免費 plugin 不是營收產品——它是一項
**生態系投資**。它的成功指標就是 framework 本身的指標：

1. **降低評估門檻。** ZK 相較於現代前端技術（React/Vue + HMR）最大的 DX 落差，
   就在於回饋迴圈：*編輯 → build → 部署 → 重新整理瀏覽器*。一個評估 ZK 的新手
   在第一個小時內就會感受到這個痛點。Preview 把這個迴圈壓縮成
   *編輯 → 存檔 → 立刻看到*，而且**完全不需設定伺服器**——它正面攻擊了
   評估者最容易放棄的那個時刻。
2. **留住既有的舊專案客群。** 相當大比例的 ZK 開發者維護的是舊有應用程式。
   由於 preview 驅動的是**專案自己的 ZK jars**（而非內建的複製版本），
   一個 ZK 8 的應用程式就會以 ZK 8 的方式呈現。這種版本一致性是任何
   通用工具都無法為這群使用者提供的。
3. **鞏固 IntelliJ 成為**那個** ZK IDE。** ZK Studio（Eclipse）已停止維護；
   這個 plugin 是目前唯一持續維護的 IDE 方案。Preview 是它第一個超越
   「更聰明的 XML 編輯」、真正做出明顯差異化的功能——它把這個 plugin
   從*便利工具*升級成*值得安裝的理由*。

**建議的定位陳述：**
> *「即時看見你的 ZUL 版面——由你專案自己的 ZK 引擎渲染，無需啟動伺服器。」*

刻意定位為**版面／結構預覽**，而非應用程式模擬器。

---

## 2. 最大的產品風險：期待落差

「preview」這個詞會讓使用者期待看到*他們的應用程式*。v1 會把綁定值渲染為
**空白**（L-2，刻意設計——使用者類別從不載入）。一個使用者打開一個
資料密集的 MVVM 頁面，看到空白的 label 和空的 grid，卻沒有任何說明，
就會判定*「壞掉了」*並留下兩星評價。**刻意設計 ≠ 被感知為刻意設計。**

依槓桿效益排序的緩解措施：

| # | 緩解措施 | 成本 | 效果 |
|---|-----------|------|--------|
| M-1 ✅ | **把綁定運算式渲染為可見的 placeholder**——例如綁定到 `@load(vm.name)` 的 label 以淡化／樣式化的文字顯示 `vm.name`，而非空白。 | 中（屬於 `PreviewUiFactory` hook 的範疇） | 把最主要的「被感知為 bug」轉化為看得見的功能：版面讀起來像帶有欄位名稱的線框圖。這是在維持隔離保證的前提下，槓桿效益最高的擬真度改進。 |
| M-2 ✅ | **在 UI、文件與 marketplace 文案中命名並框定為「Layout Preview」**——絕不用「live app preview」。 | 近乎零 | 在第一次渲染前就設定好期待。 |
| M-3 ✅ | **窗格內的說明提示**（小小的 ℹ︎／首次執行橫幅）：「綁定值以 placeholder 呈現——你的 ViewModel 不會在此執行。」 | 低 | 攔截那些跳過文件的使用者（也就是所有人）。 |

---

## 3. 依產品價值排序 limitation backlog

已記錄的 14 項 limitation 重要性並不相同。依使用者感知的影響排序：

### P0 — 稱得上 GA（非 beta）之前必須修正 ✅ 三項皆已出貨於 1.0.0
- **L-10 原始 JSON 錯誤內容。** ✅ 已做：`ErrorPageRenderer` 產生格式化錯誤頁
  （phase、訊息、`file:line`、可收合 stack trace、預填的 GitHub 回報連結，
  過長時改用剪貼簿交遞）。Preview 正是在*編輯過程中*被使用，而檔案有一半
  時間都處於壞掉的狀態。在這個功能最頻繁的接觸點丟出一段原始的 HTTP-500 JSON
  會摧毀信任。stage-2 contract（`RenderError {phase, message, line, column}`）
  已經存在——把消費它的格式化錯誤窗格做出來。這是目前最便宜的信任贏面。
- **首次開啟的失敗路徑必須是可行動的。** ✅ 已做：`NO_ZK_JARS` 與
  `STALE_CLASSPATH`（已宣告但 jar 不在磁碟上）分開給不同建議、JCEF 不可用時
  診斷出真正原因並提供「用外部瀏覽器開啟」，每張卡片都帶「回報到 GitHub」。
  每個訊息都應告訴使用者*該怎麼做*（「將 ZK 加入此 module 的 dependencies」、
  「把 WEB-INF/lib jars 掛成 library——步驟如下」），而不只是說明失敗了什麼。
  一個首次開啟就失敗的使用者，不會再打開第二次。注意 L-12：那些 jars 只放在
  `WEB-INF/lib`（從未 import 成 IntelliJ library）的舊 webapp，正是這個功能
  應該贏得的舊專案客群——那個訊息是他們唯一的橋樑。
- **L-11 首次執行延遲的框定。** ✅ 已做：等待期間窗格顯示
  「正在啟動 ZK preview server…」，且所有失敗路徑都會落到說明卡片，
  不會停在 loading。沉默會被讀成當機。

### P1 — 下一個版本
- **M-1 placeholder 渲染**（見 §2）——✅ 已做（含 grid/listbox/tree 的
  placeholder 列，以及 dimmed italic 樣式）。
- **L-9 僅在存檔時重整。** v1 可以接受（IntelliJ 使用者會反射性地存檔），
  但「隨打字即時重整」（debounced document-listener 渲染）才能讓*即時*的
  承諾成真，而且那正是行銷 GIF 的展示時刻。
- **L-8 閒置 JVM timeout。** 整個 session 期間，每個 (docroot, classpath) 就有
  一個 helper JVM，這在一位顧問同時開六個客戶專案、注意到 RAM 之前都是隱形的。
  15–30 分鐘的閒置終止 + 透明重啟是廉價的衛生措施。

### 明確排除範圍——公開講清楚，永不承諾
- **L-1 AU round-trip／互動性。** 那是 `runIde`／真實伺服器的職責。
  承諾*永遠*只做「first paint」是一個優勢：它讓安全論述
  （「你的程式碼絕不會在 IDE 中執行」）保持乾淨，維護面保持精簡。
- **L-2 真實綁定值。** 同樣的隔離保證。Placeholder（M-1）就是上限，而這樣就夠了。

---

## 4. 上線建議

> 實際出貨的是 **1.0.0**（非 beta 標籤）。第 1 項的「窗格內回饋管道」以
> 每張失敗卡片與錯誤頁上的「回報到 GitHub」連結達成；第 4 項的查詢表已寫成
> [zul-preview-feature.md](zul-preview-feature.md) 的「Supported project layouts」
> 與「Requirements」兩節。第 2、3 項（公告、marketplace listing）仍待執行。

1. **以 Beta／Experimental 標籤發佈 0.8.0**，並在 preview 窗格*內部*放一個
   回饋連結（plugin 已經有 Help ▸ ZK Feedback 的基礎設施——重用它）。
   免費 plugin + beta 標籤 = 公開迭代的許可。
2. **讓公告與 ZK 自己的通路協同。** 這個 plugin 本身就內建了一條來自 zkoss.org
   的新聞通知管線——用它來發佈自己功能的上線消息。部落格文章 + 15 秒 GIF
   （打字 → 存檔 → preview 更新）+ 更新後的 marketplace 截圖。那支 GIF 就是
   整個賣點；把資源投在那裡。
3. **更新 marketplace listing 的定位。** 今天這個 plugin 讀起來像
   「completion + validation」。以 preview 為主打：它正是把「有也不錯」
   轉化為「任何 ZK 專案第一天就安裝」的功能。
4. **把相容性矩陣（ZK 版本 × javax/jakarta × 專案類型，取自 spec §3）
   整理在一頁文件上。** 支援工單——也就是 `potential-ideas.md` 的來源——顯示
   設定上的困惑是 ZK *最*常見的痛點；別讓 preview 在沒有查詢表的情況下
   又新增一個困惑來源。

---

## 5. 如何衡量成功（免費產品 = 生態系指標）

在 JetBrains plugin 中直接做 telemetry 需要 opt-in 同意；即使沒有它，也可用：

- **Marketplace**：0.8.0 之後的下載成長、評分趨勢、評論內容。
- **GitHub issues**：「preview 在 X 環境下無法運作」（啟用問題）
  相對於「preview 也應該做 Y」（互動——那種好的抱怨）的比例。
- 若日後加入輕量的 opt-in telemetry，最重要的四個數字：preview 開啟次數、
  **渲染成功率**、`NO_ZK_JARS`／無 JCEF 的比例（啟用漏斗的漏洞）、
  首次渲染耗時。

建議的 GA 標準：錯誤窗格已上線、可行動的空狀態已上線、且多數 beta 回饋
屬於互動型（feature request）而非啟用型（activation）抱怨。

---

## 6. 較長期的產品選項（方向性，非承諾）

- **永久保持免費。** 它的價值在於作為 ZK 評估的漏斗頂端，以及既有客群的黏著度；
  收費會同時砍掉這兩者。若日後想要一個 premium 切角，它應該屬於*附加功能*
  （例如把 sample data 注入 placeholder、在 preview 中切換 theme）——
  絕不放在核心 preview 上。
- **Preview 作為平台。** 這個隔離的渲染核心（可用 CLI 執行、零 IDE 依賴）
  日後可以服務於：為文件產生截圖、在 CI 做視覺回歸測試、文件網站上的
  「live ZUL playground」。現在別做這些——但要避免會封死這些可能性的決策。
- **與 MVVM tooling 構想（`potential-ideas.md` §4）的綜效。** 一旦有了
  ViewModel-aware 的導航／驗證，preview 的 placeholder 渲染（M-1）就能顯示
  *帶型別*的 placeholder（例如從 getter 回傳型別推導），而完全不執行
  使用者程式碼——這兩條路線會互相加乘。

---

## 7. 摘要

| 問題 | 回答 |
|---|---|
| 一句話它是什麼？ | 由專案自己的 ZK 引擎渲染、零設定的版面預覽。 |
| 給誰用的？ | ZK 評估者（回饋迴圈痛點）與舊專案維護者（版本一致性）。 |
| 策略目標是什麼？ | Framework 的採用與留存；讓 IntelliJ 成為明確的 ZK IDE。 |
| 最大的風險？ | 「preview」過度承諾 → 空白的綁定值被讀成壞掉。 |
| 前三個行動？ | 1）格式化錯誤窗格（消費 stage-2 contract）。2）可行動的失敗／空狀態。3）綁定的 placeholder 渲染 + 「Layout Preview」框定。 |
| GA 標準？ | 上述三個行動已上線；beta 回饋從啟用型抱怨轉向 feature request。 |
