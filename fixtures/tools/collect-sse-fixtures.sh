#!/usr/bin/env bash
# 采集三家 Provider 的真实 SSE 报文 → fixtures/sse/
#
# 对应 docs/BASELINE_COLLECTION.md §A。用法：
#
#   cp fixtures/.env.example fixtures/.env.local   # 然后填值
#   bash fixtures/tools/collect-sse-fixtures.sh --dry-run   # 先零成本确认 Key/URL 读对了
#   bash fixtures/tools/collect-sse-fixtures.sh             # 正式采
#
# ── 关于「中转站」（本脚本已适配）──────────────────────────────
# 如果你的 Key 来自中转站（OpenAI 兼容 + Bearer 鉴权），只需：
#   WW_FIXTURE_OPENAI_BASE=https://你的中转站/v1     # 结尾带不带 /v1 都行，脚本会归一化
#   WW_FIXTURE_OPENAI_KEY=sk-xxx                     # 或沿用 DEEPSEEK_API_KEY / OPENAI_API_KEY
#   WW_FIXTURE_OPENAI_MODEL=某个中转站支持的模型名
#
# ⚠️ **中转站通常只暴露 OpenAI 兼容端点**，会把 Claude / Gemini 的响应也归一化成
#    OpenAI 形状（`choices[].delta`）。那种报文**不能**当 Claude / Gemini 的 fixture 用 ——
#    用它会写出错误的适配器，而且流式 bug 极难查。
#    只有当中转站**原样透传**原生端点时才可采：
#      Claude 原生：POST {base}/v1/messages      （x-api-key + anthropic-version）
#      Gemini 原生：POST {base}/v1beta/models/<model>:streamGenerateContent?alt=sse&key=…
#    校验器会做「文件名 vs 内容」一致性检查，发现归一化会直接判 FAIL（见 check-fixtures.mjs）。
#
# 约定：
#   - 只采「有 Key 的那几家」（§F：OpenAI 系 > Claude > Gemini），缺的跳过并提示。
#   - 本脚本**不打印任何 Key**；采集完自动跑 check-fixtures.mjs 校验判据。
#   - 采集脚本与产物都在本仓 fixtures/ 内（docs/FILE_LAYOUT.md §4 规则 2）。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT="$REPO_ROOT/fixtures/sse"
mkdir -p "$OUT"

# ---- 加载本仓密钥文件 ----
# 为什么用文件而不是 `export`：本脚本由 Freebuff 的 shell 执行，
# 你在另一个终端 export 的变量**不会**被继承。落成文件跨进程可靠。
# 手工解析（不用 `source`）是为了吃掉 Windows 的 CRLF —— 否则值后面会多个 \r，
# Authorization 头会静默失效（表现为 401，且看起来像 Key 不对）。
# 去掉右侧空白（空格 / tab）。用 %-删除尾部空白串的经典写法（不依赖 extglob）。
rstrip() { local v="$1"; v="${v%"${v##*[![:space:]]}"}"; printf '%s' "$v"; }

load_env_file() {
  local file="$1"
  [ -f "$file" ] || return 1
  local line key value
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; esac
    # 容忍 `export FOO=bar` 写法
    line="${line#export }"
    case "$line" in *=*) ;; *) continue ;; esac
    key="$(rstrip "${line%%=*}")"
    value="${line#*=}"
    value="$(rstrip "$value")"
    # 先剥引号，**再**去一次空白。顺序很重要：
    # `KEY="https://x"   ` 这种「带引号＋行尾空格」在手工编辑时极常见，
    # 若先匹配引号会因为结尾不是引号而匹配失败，引号被当成值的一部分 → 静默 401。
    case "$value" in
      \"*\"|\'*\')
        local q="${value:0:1}"
        case "${value: -1}" in
          "$q") value="${value:1:${#value}-2}" ;;
          *) ;; # 引号不配对（值里含引号）→ 原样保留，不去猜
        esac
        ;;
      *)
        # 未加引号时容忍行尾注释：只在「空白 + #」处分隔，避免误伤 URL 里的 #fragment
        case "$value" in *' #'*) value="$(rstrip "${value%% #*}")" ;; esac
        ;;
    esac
    # 只接受合法变量名，避免误执行奇怪的行
    case "$key" in ''|*[!A-Za-z0-9_]*) continue ;; esac
    export "$key=$value"
  done < "$file"
  return 0
}

for candidate in "$REPO_ROOT/fixtures/.env.local" "$REPO_ROOT/fixtures/.env"; do
  if load_env_file "$candidate"; then
    echo "已加载密钥文件：${candidate#$REPO_ROOT/}"
  fi
done

# ---- 变量解析（多种别名，兼容中转站命名习惯）----
PROMPT="${WW_FIXTURE_PROMPT:-从1数到5，每个数字后面加一句不超过10个字的短句描述}"
TOOL_PROMPT="${WW_FIXTURE_TOOL_PROMPT:-请调用 get_weather 工具查询北京的天气}"

OPENAI_BASE="${WW_FIXTURE_OPENAI_BASE:-https://api.deepseek.com}"
OPENAI_MODEL="${WW_FIXTURE_OPENAI_MODEL:-deepseek-chat}"
OPENAI_KEY="${WW_FIXTURE_OPENAI_KEY:-${DEEPSEEK_API_KEY:-${OPENAI_API_KEY:-}}}"

CLAUDE_BASE="${WW_FIXTURE_CLAUDE_BASE:-https://api.anthropic.com}"
CLAUDE_MODEL="${WW_FIXTURE_CLAUDE_MODEL:-claude-sonnet-4-5}"
CLAUDE_KEY="${WW_FIXTURE_CLAUDE_KEY:-${ANTHROPIC_API_KEY:-}}"
# 中转站有时只认 Bearer；设 WW_FIXTURE_CLAUDE_AUTH=bearer 切过去
CLAUDE_AUTH="${WW_FIXTURE_CLAUDE_AUTH:-x-api-key}"
CLAUDE_USER_AGENT="${WW_FIXTURE_CLAUDE_USER_AGENT:-}"

GEMINI_BASE="${WW_FIXTURE_GEMINI_BASE:-https://generativelanguage.googleapis.com}"
GEMINI_MODEL="${WW_FIXTURE_GEMINI_MODEL:-gemini-2.5-flash}"
GEMINI_KEY="${WW_FIXTURE_GEMINI_KEY:-${GEMINI_API_KEY:-${GOOGLE_API_KEY:-}}}"

# ---- URL 归一化：结尾带不带 /v1、带不带完整端点都要能吃 ----
normalize_chat_url() {  # OpenAI 兼容
  local base="${1%/}"
  case "$base" in
    */chat/completions) printf '%s' "$base" ;;
    */v1)               printf '%s/chat/completions' "$base" ;;
    *)                  printf '%s/v1/chat/completions' "$base" ;;
  esac
}
normalize_messages_url() {  # Claude 原生
  local base="${1%/}"
  case "$base" in
    */messages) printf '%s' "$base" ;;
    */v1)       printf '%s/messages' "$base" ;;
    *)          printf '%s/v1/messages' "$base" ;;
  esac
}
normalize_gemini_url() {  # Gemini 原生（必须带 ?alt=sse）
  local base="${1%/}"
  case "$base" in
    *:streamGenerateContent*) printf '%s&key=%s' "$base" "$GEMINI_KEY" ;;
    */v1beta) printf '%s/models/%s:streamGenerateContent?alt=sse&key=%s' "$base" "$GEMINI_MODEL" "$GEMINI_KEY" ;;
    *)        printf '%s/v1beta/models/%s:streamGenerateContent?alt=sse&key=%s' "$base" "$GEMINI_MODEL" "$GEMINI_KEY" ;;
  esac
}

OPENAI_URL="$(normalize_chat_url "$OPENAI_BASE")"
CLAUDE_URL="$(normalize_messages_url "$CLAUDE_BASE")"
GEMINI_URL="$(normalize_gemini_url "$GEMINI_BASE")"

# ---- dry-run：只报告会采哪几家与解析出的 URL，不发请求 ----
DRY_RUN=0
case "${1:-}" in --dry-run|-n) DRY_RUN=1 ;; esac
[ "${WW_FIXTURE_DRY_RUN:-0}" = "1" ] && DRY_RUN=1

if [ "$DRY_RUN" = "1" ]; then
  echo
  echo "=== dry-run：只报告配置解析结果，不发任何请求 ==="
  # URL 里可能含 key（Gemini 是 query 参数）——打印前把任何已配置的 key 替换掉。
  # 注意：绝不能对空 pattern 做 ${var//$EMPTY/x}，空 pattern 会逐字符插入替换串。
  mask() {
    local s="$1" k
    for k in "$GEMINI_KEY" "$OPENAI_KEY" "$CLAUDE_KEY"; do
      [ -n "$k" ] || continue
      s="${s//$k/<KEY>}"
    done
    printf '%s' "$s"
  }
  for pair in "openai:WW_FIXTURE_OPENAI_KEY|DEEPSEEK_API_KEY|OPENAI_API_KEY:$OPENAI_KEY:$OPENAI_URL" \
              "claude:WW_FIXTURE_CLAUDE_KEY|ANTHROPIC_API_KEY:$CLAUDE_KEY:$CLAUDE_URL" \
              "gemini:WW_FIXTURE_GEMINI_KEY|GEMINI_API_KEY|GOOGLE_API_KEY:$GEMINI_KEY:$GEMINI_URL"; do
    name="${pair%%:*}"; rest="${pair#*:}"
    vars="${rest%%:*}"; rest="${rest#*:}"
    key="${rest%%:*}"; url="${rest#*:}"
    if [ -n "$key" ]; then
      echo "  ✅ $name → 将采集（$vars，key 长度 ${#key}）"
    else
      echo "  ⏭  $name → 跳过（未设 $vars）"
    fi
    echo "       URL: $(mask "$url")"
  done
  if [ "$CLAUDE_AUTH" = "x-api-key" ]; then
    echo "       （Claude 鉴权用 x-api-key；中转站只认 Bearer 时设 WW_FIXTURE_CLAUDE_AUTH=bearer）"
  else
    echo "       （Claude 鉴权用 Authorization: Bearer）"
  fi
  echo "       Claude model: $CLAUDE_MODEL"
  if [ -n "$CLAUDE_USER_AGENT" ]; then
    echo "       Claude User-Agent: $CLAUDE_USER_AGENT"
  fi
  echo
  echo "模板：fixtures/.env.example　实际填写：fixtures/.env.local"
  exit 0
fi

COLLECTED=(); SKIPPED=(); FAILED=()

post() {  # $1=outfile $2...=curl args；回显 HTTP 码
  local out="$1"; shift
  curl -sS -N --max-time 180 -X POST "$@" -o "$out" -w '%{http_code}' 2>/dev/null
}

note_result() {  # $1=label $2=code $3=file
  if [ "$2" = "200" ]; then
    echo "  ✅ $1 → $(basename "$3")"; COLLECTED+=("$1")
  else
    mkdir -p "$OUT/_rejected"
    local rejected="$OUT/_rejected/$(basename "$3").http-$2"
    mv "$3" "$rejected"
    echo "  ❌ $1 HTTP $2（响应已隔离到 fixtures/sse/_rejected/）"; FAILED+=("$1")
  fi
}

echo "=== 采集 SSE 报文 → $OUT ==="

# ---------- ① OpenAI 兼容系（含中转站）----------
if [ -n "$OPENAI_KEY" ]; then
  echo "[openai] $OPENAI_URL  模型=$OPENAI_MODEL"

  body=$(printf '{"model":"%s","stream":true,"messages":[{"role":"user","content":"%s"}]}' \
    "$OPENAI_MODEL" "$PROMPT")
  code=$(post "$OUT/openai-stream.txt" "$OPENAI_URL" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $OPENAI_KEY" \
    --data-binary "$body")
  note_result "openai-stream" "$code" "$OUT/openai-stream.txt"

  body=$(printf '{"model":"%s","stream":true,"messages":[{"role":"user","content":"%s"}],"tools":[{"type":"function","function":{"name":"get_weather","description":"查询天气","parameters":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}}]}' \
    "$OPENAI_MODEL" "$TOOL_PROMPT")
  code=$(post "$OUT/openai-toolcall-stream.txt" "$OPENAI_URL" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $OPENAI_KEY" \
    --data-binary "$body")
  note_result "openai-toolcall-stream" "$code" "$OUT/openai-toolcall-stream.txt"
else
  echo "[openai] 跳过：未设 WW_FIXTURE_OPENAI_KEY / DEEPSEEK_API_KEY / OPENAI_API_KEY"
  SKIPPED+=("openai")
fi

# ---------- ② Claude（需**原生**端点；中转站多数不透传）----------
if [ -n "$CLAUDE_KEY" ]; then
  echo "[claude] $CLAUDE_URL  模型=$CLAUDE_MODEL"

  USER_AGENT_ARGS=()
  if [ -n "$CLAUDE_USER_AGENT" ]; then
    USER_AGENT_ARGS=(-H "User-Agent: $CLAUDE_USER_AGENT")
  fi

  if [ "$CLAUDE_AUTH" = "bearer" ]; then
    AUTH_ARGS=(-H "Authorization: Bearer $CLAUDE_KEY")
  else
    AUTH_ARGS=(-H "x-api-key: $CLAUDE_KEY")
  fi

  body=$(printf '{"model":"%s","max_tokens":256,"stream":true,"messages":[{"role":"user","content":"%s"}]}' \
    "$CLAUDE_MODEL" "$PROMPT")
  code=$(post "$OUT/claude-stream.txt" "$CLAUDE_URL" \
    -H "Content-Type: application/json" \
    -H "anthropic-version: 2023-06-01" \
    "${AUTH_ARGS[@]}" \
    "${USER_AGENT_ARGS[@]}" \
    --data-binary "$body")
  note_result "claude-stream" "$code" "$OUT/claude-stream.txt"

  body=$(printf '{"model":"%s","max_tokens":256,"stream":true,"messages":[{"role":"user","content":"%s"}],"tools":[{"name":"get_weather","description":"查询天气","input_schema":{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}}]}' \
    "$CLAUDE_MODEL" "$TOOL_PROMPT")
  code=$(post "$OUT/claude-toolcall-stream.txt" "$CLAUDE_URL" \
    -H "Content-Type: application/json" \
    -H "anthropic-version: 2023-06-01" \
    "${AUTH_ARGS[@]}" \
    "${USER_AGENT_ARGS[@]}" \
    --data-binary "$body")
  note_result "claude-toolcall-stream" "$code" "$OUT/claude-toolcall-stream.txt"

  if [ "${WW_FIXTURE_WITH_THINKING:-0}" = "1" ]; then
    body=$(printf '{"model":"%s","max_tokens":2048,"stream":true,"thinking":{"type":"enabled","budget_tokens":1024},"messages":[{"role":"user","content":"%s"}]}' \
      "$CLAUDE_MODEL" "$PROMPT")
    code=$(post "$OUT/claude-thinking.txt" "$CLAUDE_URL" \
      -H "Content-Type: application/json" \
      -H "anthropic-version: 2023-06-01" \
      "${AUTH_ARGS[@]}" \
      "${USER_AGENT_ARGS[@]}" \
      --data-binary "$body")
    note_result "claude-thinking" "$code" "$OUT/claude-thinking.txt"
  fi
else
  echo "[claude] 跳过：未设 WW_FIXTURE_CLAUDE_KEY / ANTHROPIC_API_KEY"
  SKIPPED+=("claude")
fi

# ---------- ③ Gemini（需**原生**端点 + ?alt=sse）----------
if [ -n "$GEMINI_KEY" ]; then
  echo "[gemini] $GEMINI_MODEL"

  body=$(printf '{"contents":[{"parts":[{"text":"%s"}]}]}' "$PROMPT")
  code=$(post "$OUT/gemini-stream.txt" "$GEMINI_URL" \
    -H "Content-Type: application/json" \
    --data-binary "$body")
  note_result "gemini-stream" "$code" "$OUT/gemini-stream.txt"
else
  echo "[gemini] 跳过：未设 WW_FIXTURE_GEMINI_KEY / GEMINI_API_KEY"
  SKIPPED+=("gemini")
fi

# ---------- 汇总 ----------
echo
echo "=== 采集汇总 ==="
echo "成功：${#COLLECTED[@]} 个${COLLECTED[*]:+（${COLLECTED[*]}）}"
[ ${#SKIPPED[@]} -gt 0 ] && echo "跳过：${#SKIPPED[@]} 家（${SKIPPED[*]}）—— 对应适配器 T04 暂不写"
[ ${#FAILED[@]} -gt 0 ] && echo "失败：${#FAILED[@]} 个（${FAILED[*]}）—— 多为模型名不对 / Key 无效 / 端点不是原生（看 §A.3）"

if [ ${#COLLECTED[@]} -eq 0 ]; then
  echo
  echo "⚠️ 一个都没采到。填好 fixtures/.env.local 后重跑；先 --dry-run 可只看配置解析结果。"
  exit 2
fi

# ---- 防「中转站毒化」：归一化过的报文不能留在原地 ----
# 中转站常把 Claude/Gemini 的响应也归一化成 OpenAI 形状。若文件名声称 claude/gemini
# 而内容却是 choices 形状，**必须挪走**：留着的话，后续很容易被当成真实协议样本写适配器，
# 而由此产生的流式 bug 极难排查（MIGRATION_CONSTRAINTS.md 开头警告的事）。
sniff_provider() {
  local f="$1"
  if grep -q 'content_block_delta\|content_block_start' "$f" 2>/dev/null; then printf 'claude'
  elif grep -q '"candidates"' "$f" 2>/dev/null; then printf 'gemini'
  elif grep -q '"choices"' "$f" 2>/dev/null; then printf 'openai'
  else printf 'unknown'; fi
}

REJECTED_DIR="$OUT/_rejected"
for f in "$OUT"/claude-*.txt "$OUT"/gemini-*.txt; do
  [ -f "$f" ] || continue
  base="$(basename "$f")"
  want='claude'; case "$base" in gemini-*) want='gemini' ;; esac
  got="$(sniff_provider "$f")"
  if [ "$got" != "$want" ] && [ "$got" != 'unknown' ]; then
    mkdir -p "$REJECTED_DIR"
    mv "$f" "$REJECTED_DIR/"
    echo "  ⚠️  $base 的内容是 $got 形状（中转站把它归一化了）→ 已移入 fixtures/sse/_rejected/"
    echo "      它**不能**当 $want 的 fixture 用；要采真正的 $want 报文需中转站原生透传。"
  fi
done

# `_rejected/` 是子目录，校验器默认只扫 sse/ 直下，不会误扫它。
echo
echo "=== 自动校验（BASELINE_COLLECTION §A.3 + 文件名/内容一致性）==="
CHECK_STATUS=0
if node "$SCRIPT_DIR/check-fixtures.mjs"; then
  if [ ${#FAILED[@]} -eq 0 ]; then
    echo "✅ 校验全部通过 —— 可以拿去写 T04 的适配器单测了。"
  else
    echo "⚠️ 当前保留的 fixtures 校验通过，但本轮有采集请求失败；失败的 Provider 尚无新样本。"
  fi
else
  CHECK_STATUS=$?
  echo "⚠️ 校验有 FAIL（看上方 ✗ 行）。**不要拿 FAIL 的报文去写适配器**："
  echo "   多于 1 行的 data: 但内容是全文 → 上游关了 stream；乱码 → 带了 gzip；"
  echo "   完整 JSON 对象 → Gemini 忘加 ?alt=sse；文件名与内容不符 → 中转站归一化。"
fi

echo
echo "下一步：把「采集来源记录」表补进 fixtures/sse/README.md（模型名 / 采集日期 / 备注，"
echo "        中转站要写明「是否原生透传」）。"

if [ "$CHECK_STATUS" -ne 0 ] || [ ${#FAILED[@]} -gt 0 ]; then
  exit 1
fi
