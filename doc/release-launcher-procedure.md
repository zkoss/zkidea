# 發布 zk-preview-launcher — 執行程序（通用版）

給執行者（人或 AI）的明確指示。**照順序做，不要跳步。**

本文件在 1.0.2 發布後改寫為通用版：把當時實際踩到的兩個坑寫進正式步驟，
並把「理論上可能」的警告換成「已經實測發生過」的事實。
1.0.2 當次的實際數值與經過收在第 9 節。

以下用 `<VER>` 代表要發的版本（例如 `1.0.3`），`v<VER>` 代表對應的 tag。

---

## 0. 一句話說明這份文件為什麼存在

發布 jar 本身是自動化的，但發布**之後**必須手動更新**另一個 repo** 裡的一個常數。
漏掉那一步，使用者的第一次預覽會從「軟失敗」變成「硬失敗」—— 比不發布還糟。

---

## 1. 兩個 repo、兩個角色

| 角色 | Repo | 在本程序中的作用 |
|---|---|---|
| 生產者 | <https://github.com/zkoss/zkidea> | 建置並發布 jar 到 GitHub Release |
| 消費者 | <https://github.com/zkoss-demo/agent-skill> | 釘住版本＋SHA-256 並下載該 jar |

兩邊都要有一份本機工作副本。本文件後續一律用這兩個變數指稱它們，
**開始之前先設好，指向你自己的工作副本：**

```bash
ZKIDEA=<你的 zkoss/zkidea 工作副本路徑>
SKILL=<你的 zkoss-demo/agent-skill 工作副本路徑>
```

**需要手動編輯的檔案**（消費者 repo 內的相對路徑）：

```
skills/zul-writer/scripts/preview-zul.py
```

<https://github.com/zkoss-demo/agent-skill/blob/main/skills/zul-writer/scripts/preview-zul.py>

**需要改的欄位**（三個常數放在一起，用 `grep -n LAUNCHER_VERSION` 找）：

```python
LAUNCHER_VERSION = "<VER>"     # 換版時要改
LAUNCHER_SHA256  = "<...>"     # 每次都要改
LAUNCHER_URL     = (...)       # 由 VERSION 組出來，永遠不要手動改
```

> 若這次只是重發同一版號（不建議，見第 3 節第 2 點），則 `LAUNCHER_VERSION` 不動，
> 只改 `LAUNCHER_SHA256`。

---

## 2. 為什麼一定要改 digest 這個欄位

`preview-zul.py` 對 launcher jar 的信任分兩條路，規則不同：

| 取得方式 | digest 不符時 |
|---|---|
| 使用者用 `--launcher-jar` 手動指定 | **只警告**，仍然執行（是使用者自己選的位元組） |
| 腳本自己從 GitHub 下載 | **致命失敗**，拒絕執行 |

第二條路的程式碼註解原文：

> Nobody chose these bytes, so nothing unverified is ever executed on this path:
> **a digest mismatch is fatal, not advisory.**

**若只發布 jar 而不改 `LAUNCHER_SHA256`**：下載成功 → digest 不符 → 硬失敗，
而且錯誤訊息會讓使用者以為檔案被竄改。**這是唯一會讓情況倒退的錯誤，務必避免。**

---

## 3. 絕對不要做的三件事

1. **不要從本機建置結果抄 digest。**
   **這不是理論風險，1.0.2 已經實測證實過**：同一個 commit，
   CI（ubuntu ＋ Temurin 17）產出 `d451589f…`，macOS 本機產出 `5a33e2ba…` —— 兩者不同。
   digest 必須從**已發布資產旁的 `.sha256`** 抄，
   *"never from a local rebuild, which can differ byte-for-byte while being functionally identical."*
2. **不要對已經發布過的 tag 重跑 workflow。** 該 job 用 `gh release upload --clobber`，
   會覆蓋消費者已經釘住的 URL 上的位元組。workflow 自己的註解稱這是
   *"a deliberate pin-breaker: bump the version instead of re-cutting the same tag."*
   若某版已發布且需要改內容 → **改發下一個版號**。
3. **不要用本機 `gh` 直接建 Release。** workflow 刻意改用 Actions 的 `github.token`。

---

## 4. 權限前提

本程序需要一個對 `zkoss/zkidea` 有 **push 權限**的身分，兩處都要：

* `git push`（步驟 1）—— 走 git 的推送憑證。
* `gh workflow run`（步驟 2）—— 走 `gh` 目前登入的帳號。手動觸發 workflow 需要 push 權限。

**這兩者未必是同一個身分**，所以請在開始前各自確認一次：

```bash
gh auth status                       # gh 目前是誰
gh api repos/zkoss/zkidea --jq .permissions   # 該身分對本 repo 的權限
git -C "$ZKIDEA" push --dry-run origin master # git 這條路推不推得動
```

`gh` 若登入了多個帳號，用 `gh auth switch` 切到有權限的那個，
或在單一指令前面帶 `GH_TOKEN=$(gh auth token -u <帳號>)`。

> 注意：**觸發 workflow 只需要 push 權限，但查詢／修改 repo 的 Actions 設定需要 admin。**
> 若某個 `gh api` 呼叫回 403 但推送與觸發都正常，多半就是撞到這條界線，不影響本程序。

---

## 5. 前置檢查（全部通過才往下走）

```bash
cd "$ZKIDEA"

# 5.1 兩個 build.gradle 的版本都必須是 <VER>
grep -n "^version" build.gradle                      # 預期：version '<VER>'
grep -n "^version" zk-preview-launcher/build.gradle  # 預期：version '<VER>'

# 5.2 v<VER> 這個 tag 必須「還不存在」
git tag -l "v<VER>"
git ls-remote --tags origin | grep "v<VER>" || echo "remote 沒有 v<VER> — 正確"

# 5.3 確認要發的 commit
git status --porcelain       # 空的（或只有與發布無關、你確定不入版的檔案）
git log --oneline -1
```

若 5.1 任一個不是 `<VER>` → **停止**。workflow 會擋下來
（"Refuse to publish if the tag and build.gradle disagree"），先修版本再回來。

---

## 6. 步驟

### 步驟 1 — 推送 master 與 tag

```bash
cd "$ZKIDEA"
git tag v<VER>
git push origin master
git push origin v<VER>
```

先推 master 再推 tag：這樣 Release 是從真的在 master 上的 commit 建出來的，
手動觸發（步驟 2）也才選得到。

### 步驟 2 — 確認 workflow 有跑；沒跑就手動觸發

> **1.0.2 的經驗：推 tag 並沒有觸發 workflow。**
> 當時 master、tag、workflow 檔案、觸發條件全部正確，但這個 repo 的 Actions
> 執行次數是 0。最合理的解釋是：那是本 repo 第一個 workflow 檔案，
> GitHub 註冊新 workflow 是非同步的，tag 事件抵達時還沒登記到。
> 理論上第二次之後就會正常，但**務必確認**，不要假設。

```bash
gh run list --repo zkoss/zkidea --workflow "Release zk-preview-launcher" --limit 3
```

**列表是空的 → 手動觸發**（這是正式備援，不是例外處理）：

```bash
gh workflow run release-launcher.yml \
  --repo zkoss/zkidea --ref master -f tag=v<VER>
```

然後等它跑完（約 2 分鐘）：

```bash
gh run watch <RUN_ID> --repo zkoss/zkidea --exit-status --interval 15
```

### 步驟 3 — 確認兩個資產都在

```bash
gh release view v<VER> --repo zkoss/zkidea --json assets --jq '.assets[].name'
```

**驗收**：同時包含 `zk-preview-launcher-<VER>.jar` 與 `zk-preview-launcher-<VER>.jar.sha256`。
少了 `.sha256` 就**不要往下走** —— 那個 sidecar 是唯一的 digest 來源。

### 步驟 4 — 從「已發布的資產」取得權威 digest，並自行複驗

**這是本程序的核心步驟。不要用本機建置的值（第 3 節第 1 點）。**

```bash
WORK=$(mktemp -d) && cd "$WORK"
gh release download v<VER> --repo zkoss/zkidea --dir . \
  --pattern 'zk-preview-launcher-<VER>.jar' \
  --pattern 'zk-preview-launcher-<VER>.jar.sha256'

cat zk-preview-launcher-<VER>.jar.sha256        # sidecar 宣稱的值
shasum -a 256 zk-preview-launcher-<VER>.jar     # 實際算出來的值
shasum -a 256 -c zk-preview-launcher-<VER>.jar.sha256   # 必須輸出 OK
```

前兩者的 64 字元十六進位值**必須相同**，且第三行必須印出 `OK`。
不相同 → **停止**，發布有問題，不要改消費者端。

把該值記為 **`<PUBLISHED_SHA256>`**。

### 步驟 5 — 更新消費者端的常數

編輯 `$SKILL/skills/zul-writer/scripts/preview-zul.py`：

```python
LAUNCHER_VERSION = "<VER>"                 # 換版才改
LAUNCHER_SHA256  = "<PUBLISHED_SHA256>"    # 每次都改
```

`LAUNCHER_URL` **維持不動**（它是從 `LAUNCHER_VERSION` 組出來的）。

```bash
grep -n "LAUNCHER_VERSION\|LAUNCHER_SHA256" \
  "$SKILL/skills/zul-writer/scripts/preview-zul.py"
```

### 步驟 6 — 驗證下載路徑（唯一真正測到這次改動的方法）

必須**清掉快取**並且**不要帶 `--launcher-jar`**，否則走的是手動路徑，測不到任何東西。

```bash
rm -rf ~/.cache/zul-writer/launcher/<VER>
# 不想刪東西的話，改用官方支援的覆寫（效果相同）：
#   export ZUL_WRITER_CACHE_DIR=$(mktemp -d)

cd "$SKILL"
uv run skills/zul-writer/scripts/preview-zul.py \
  --width 1280 --out "$WORK/verify.png" \
  zulwriter-showcase/src/main/webapp/preview-fixtures/healthy-page.zul
```

**驗收（全部要成立）：**

* `STATUS: ok`，離開碼 0
* `LAUNCHER:` 顯示 `<VER> (downloaded)` —— 括號裡**不是** `--launcher-jar`
* **沒有** `is not the pinned launcher` 警告 —— 這是本次改動成功的直接證據
* **沒有** `PREVIEW_SKIPPED`
* 輸出是真的 PNG（`file` 指令確認）

再跑一次，`LAUNCHER:` 應變成 `<VER> (cache)`，且快取裡那份 jar 的
digest 要等於 `<PUBLISHED_SHA256>`：

```bash
shasum -a 256 ~/.cache/zul-writer/launcher/<VER>/zk-preview-launcher.jar
```

### 步驟 7 — 跑完整的命令列合約套件

```bash
cd "$SKILL"
ZUL_WRITER_LAUNCHER_JAR="$WORK/zk-preview-launcher-<VER>.jar" \
  python3 test/run-preview-tests.py
```

**驗收**：`0 failed`、`Result: ✓ CLI contract holds`、離開碼 0。

> 前面的檢查項數會隨套件擴充而變（1.0.2 當時 20 項，1.0.3 時已是 29 項），
> **不要拿數字當驗收條件** —— 判準是「0 failed 且離開碼 0」。

> **陷阱 1**：這個套件用 `sys.executable` spawn 子行程，**必須**用一個裝了
> `playwright` 的直譯器。用沒有 playwright 的 python 會得到 18/20 失敗，
> 看起來像合約壞掉，其實只是直譯器不對。先確認：
> ```bash
> python3 -c "import playwright; print('ok')"
> ```
> 沒有的話：
> ```bash
> uv venv "$WORK/pwenv" --python 3.12
> uv pip install --python "$WORK/pwenv/bin/python" "playwright>=1.44"
> ```
>
> **陷阱 2（1.0.2 踩過，已修）**：這套測試曾經把 `WARNINGS` 區塊當成必定出現，
> 因為當時釘的 digest 永遠對不上，每次都會噴指紋警告。指紋修正後警告消失，
> A1 golden page 反而會失敗 —— 「修好了才失敗」。
> 現在 `BLOCK_ORDER` 已把 `WARNINGS` 改為選配，兩種情況都應該全數通過。
> 若又看到 A1 因區塊順序失敗，先確認是不是同一類的過期期待，再懷疑真的壞了。

### 步驟 8 — 提交消費者端的改動

```bash
cd "$SKILL"
git add skills/zul-writer/scripts/preview-zul.py
git commit -m "fix(zul-writer): pin the digest of the published <VER> launcher"
```

**只 commit 這一個檔案。** 不要把工作區其他無關檔案一起帶進去。
不要 push —— 由維護者自行推送。

---

## 7. 完成後的驗收清單

- [ ] `v<VER>` tag 已存在於 `zkoss/zkidea`
- [ ] Release `v<VER>` 同時含 `.jar` 與 `.jar.sha256` 兩個資產
- [ ] `shasum -a 256 -c` 對下載回來的 jar 輸出 `OK`
- [ ] `preview-zul.py` 的 `LAUNCHER_SHA256` 等於**已發布**資產的 digest
- [ ] `LAUNCHER_VERSION` 為 `"<VER>"`，`LAUNCHER_URL` 未被手動改動
- [ ] 清空快取後、**不帶** `--launcher-jar` 的預覽 `STATUS: ok`，且**沒有** `is not the pinned launcher` 警告
- [ ] 快取中的 jar digest 等於已發布的 digest
- [ ] 命令列合約套件 `0 failed`、離開碼 0（項數不固定，見步驟 7）
- [ ] 只提交了 `preview-zul.py` 一個檔案

---

## 8. 出問題時

| 症狀 | 意義 | 處理 |
|---|---|---|
| `git push` 被拒（`Permission … denied`） | git 用的憑證不是有 push 權限的那個身分 | 見第 4 節，確認 git 憑證與 `gh` 帳號 |
| 推了 tag 但 `gh run list` 是空的 | workflow 沒被觸發（1.0.2 就這樣） | 用步驟 2 的 `gh workflow run` 手動觸發 |
| `gh` 指令回 403 | 目前登入的帳號權限不足，或該呼叫需要 admin | 見第 4 節：`gh auth switch`；若推送與觸發都正常則可忽略 |
| workflow 在 "Refuse to publish…" 失敗 | tag 名與 `zk-preview-launcher/build.gradle` 的 `version` 不一致 | 刪掉 tag（本地與 remote），修正版本，重新 tag |
| Release 只有 `.jar`，沒有 `.sha256` | `releaseLauncher` 沒產出 sidecar | **不要**繼續改消費者端；先修 workflow／gradle task |
| 步驟 4 的兩個值不一致 | 上傳的位元組與 sidecar 不符 | **停止**。這是發布本身有問題 |
| 步驟 6 出現 `is not the pinned launcher` | 常數改錯，或抄成了本機建置的值 | 回到步驟 4，重新從**已發布**的 sidecar 取值 |
| 步驟 6 出現 `PREVIEW_SKIPPED … HTTP 404` | Release 或資產檔名不對 | 資產檔名必須正好是 `zk-preview-launcher-<VER>.jar`，tag 必須正好是 `v<VER>` |
| 步驟 7 幾乎全數失敗（1.0.2 當時是 18/20） | 直譯器沒有 playwright | 見步驟 7 陷阱 1 |
| 需要重發同一版 | `--clobber` 會破壞已釘住的 URL | **改發下一個版號**，不要重跑同一個 tag |

手動發布的備援程序記錄在 `zk-preview-launcher/README.md`。

---

## 9. 附錄：1.0.2 當次的實際結果（2026-08-28）

| 項目 | 值 |
|---|---|
| Release | <https://github.com/zkoss/zkidea/releases/tag/v1.0.2> |
| 發布的 jar digest（**權威值**） | `d451589f8d0e447599a96240fb17cef5b39e1575596bdc71a5bd9ad7b0d3fb7e` |
| jar 大小 | 484,343 bytes |
| 同一 commit 的 macOS 本機建置 digest | `5a33e2ba880212c5f721b4d612503013902e84071d935fef3e43739fe99fecaa` ← **與 CI 不同，這就是第 3 節第 1 點的實證** |
| 發布前消費者端釘的舊值（從未發布過） | `bab6493c2168e909e562299e041c9b3d2bb7719b7ad1c145b5db0dd365ea5b82` |
| workflow run | 33141210619，手動觸發，1 分 45 秒成功 |
| 快取位置 | `~/.cache/zul-writer/launcher/<VER>/`（可用 `ZUL_WRITER_CACHE_DIR` 覆寫） |

當次遇到並已寫回正式步驟的兩件事：

1. 推 tag 沒有觸發 workflow → 步驟 2 把手動觸發升格為正式路徑。
2. 合約套件因為「指紋修好了」而失敗 → 已修 `run-preview-tests.py`
   的 `BLOCK_ORDER`，把 `WARNINGS` 改為選配；步驟 7 陷阱 2 記錄了來龍去脈。
