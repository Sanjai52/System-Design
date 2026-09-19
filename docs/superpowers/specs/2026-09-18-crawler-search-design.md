# Page Indexing, Ranking & Search — Design (2026-09-18)

## Goal
Index crawled page text into a Redis inverted index at crawl time, rank with TF-IDF, and expose search over all crawled pages via API + page. Builds on Phase 1 multithreading: indexing runs inside workers and must be lock-free (it is — Redis atomic ops only).

## 1. Tokenizer
New `service/TextTokenizer.java` (pure static): lowercase input; split on `[^a-z0-9]+`; drop tokens shorter than 3 chars; drop English stop words (`a, an, the, and, or, of, to, in, on, for, with, is, are, was, were, be, been, by, as, at, from, that, this, it, its, into, over, after, before, between, through, during, such, other, than, then, there, their, what, which, when, where, while, about, also, just, like, more, most, only, own, same, still, even`); cap at 10,000 tokens per page (bound memory). Output `Map<String,Integer>` term→frequency. Title is stored for display but ranked on body text only.

## 2. Index model (Redis, additive — no existing keys change)
- `index:term:{token}` ZSET member=url score=tf (term frequency in that page).
- `index:doc:{urlHash}` HASH `{url, title, snippet (first 200 chars of body text), length (total tokens), jobId}`. `urlHash` = `String.valueOf(url.hashCode())`, same scheme as crawl results.
- `index:docs` SET of urlHashes (new-doc detection via `SADD` return).
- `index:meta` HASH `{totalDocs}` (`HINCRBY` only when `SADD` reports new).
- Re-crawls overwrite term scores and doc fields (same keys); `totalDocs` unchanged.

## 3. Indexing flow
`CrawlWorker.processUrl` success path only: after extracting links, build tf-map from `document.body().text()` + title, call `CrawlStateRepository.indexPage(url, jobId, title, snippet, length, tfMap)`. All Redis writes (`ZADD` per term, `HSET` doc, `SADD`+conditional `HINCRBY`) are atomic — N workers index concurrently with no locks. Failed fetches index nothing. `PageFetcherService` max body 1MB already bounds input size.

## 4. Ranking (TF-IDF at query time, Java)
New `service/RankService.java`: tokenize query with the same tokenizer (empty → empty result); for each token fetch ZSET members+scores (`ZRANGE withscores`) and `ZCARD` as df; `N` from `index:meta.totalDocs`; `score(d) = Σ tf(t,d)/len(d) × ln(N/df(t))`, missing terms contribute 0, single-doc corpora (`N=1`, `df=1`) score 0 and fall back to tf ordering — specified so tests are deterministic. Sort desc, tie-break by URL asc. Pure static `combineScores(...)` for unit tests; thin `search(query, limit)` orchestrates Redis reads.

## 5. API + UI
- `GET /api/search?q=...&limit=10` (limit default 10, max 50) → 200 `[{url, title, snippet, score}]`; 400 `{error}` on blank q; empty index or no matches → 200 `[]`. Route `/api/search` has no collision (`/api/crawl/*` is a different base).
- New `static/search.html` (same dark theme/collapsible pattern family as hierarchy.html, simpler: input + ranked cards showing score, title, snippet, link) + "Search Pages" button on home header next to "View Hierarchy".

## 6. Testing
Unit (mocked repo, no Redis): tokenizer rules (case, split, stop words, min length, cap); TF-IDF ordering on canned docs (higher tf ranks first; idf weighting prefers rarer terms); blank-q 400; unknown term → `[]`. Live on :8900: crawl `example.com` → search "example" returns ranked hits with title/snippet/score; search gibberish → `[]`. All prior tests stay green; JMeter unaffected (additive endpoint).

## Out of scope
Per-job index scoping (global index; jobId shown per hit), phrase/proximity search, stemming, snippet highlighting, pagination beyond limit, index deletion API.
