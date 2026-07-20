採用 Planner、Generator、Evaluator 的 pattern 幫我設計一個 workflow。

你現在扮演的是 Planner 角色，Generator 跟 Evaluator 各自用不同的 model、不同的 subagent 來做

我的目標是要在這個 plugin 中做出一個新功能 「ZUL Preview」，可以預覽 ZUL 在瀏覽器中繪製的效果。最後的功能應該要包含：

1. 一個可以預覽 ZUL 結果的 UI 介面
一般預覽頁面通常的做法是，當開啟 .zul 檔案的時候，下方會出現一個獨立的頁籤，點開後就是預覽介面。
這是一般的做法供你參考，不一定要照我的規格，請用 IntelliJ 常見且穩定的做法來設置即可。
2. 背後的繪製核心
這個繪製核心應該要保持獨立，未來可以被單獨呼叫

請規劃幾種可行的方案，若不確定可行性，可以採取並行開發的策略：每一種方案都實作重要且關鍵的功能，之後再透過驗收測試評估，來決定最終採用哪一種方案。

我把我所知道的需要考量的點列在下面，請在規劃過程中一併考量，但並不只限於這些：

1. 預覽範圍與依賴隔離
   我們只做 ZUL 繪製結果的預覽，因此要避免受使用者專案中其他 Class 的影響。例如：
   (a) 頁面上的 Domain Object：可能是透過 Data Binding 或 EL Expression 引入的，這些可能無法真的去評估其值，因為這往往需要載入更多的 Class。
   (b) Composer 或 ViewModel：這部分可能也無法載入。因為這些組件通常依賴底層服務端的其他 Class，而服務端又會依賴 DAO 層；一旦載入 Controller，可能就會導致後面一大串 Class 都必須載入，這會使預覽畫面變得非常困難。

2. Classpath 的處理問題
   如果我們採用直接透過 ZK 現有的 Class 來解析並繪製畫面（例如讓它跑在一個內嵌的 Application Server，如 Jetty , Undertow），這時 ZK 的 JAR 包來源有兩個考量：
   (a) 專案本身份路徑：第一個想法是從專案本身的 Classpath 來抓取。但如果該專案是一個獨立的模組化專案，可能就沒有 ZK 的 Classpath。
   (b) 外部抓取：這時可以考慮從 Maven Repository 抓取 ZK EE Evaluation 版本。這雖然可能需要額外設定，但能確保環境完整。

建議第一版預設可以先採用專案內建的 Classpath 去讀取既有的 ZK JAR，這樣繪製出來的版本也會跟專案所引用的 ZK JAR 版本一致。

第二階段再規劃一個 Fail Render 回報功能，讓使用者可以回報他失敗的記錄。

這項功能有助於我們得知更多 use case，因此要設法想出一個機制，讓使用者能方便地回傳失敗的資料內容。