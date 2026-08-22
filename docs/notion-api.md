# Notion API — confirmed facts

**Written in:** M4.1, on **2026-08-22**, by reading the live reference at `developers.notion.com`.
**Status:** ✅ complete for what M4.2–M4.8 need. Two items are explicitly left open — see §10.

> `CLAUDE.md` §3.2: **do not write any of this from memory.** Everything below was read from the
> live docs on the date above and is quoted or paraphrased from them. Where two pages of the
> official docs disagreed, §11 records which one won and why.
>
> No token appears in this file. Examples use `ntn_***`.

---

## 1. The headline finding — the schema lives on a **data source**, not a database

`CLAUDE.md` §3.2 warned that "newer API versions restructured databases into **data sources**, which
moved the query endpoint and changed some response shapes. Confirm this *before* designing the
client." **It happened, in version `2025-09-03`, and the version we pin is later than that.**

A database now *contains* one or more data sources. The properties — which for this app are the
habits — belong to the **data source**, and so does the query endpoint.

```
Database  (what the user sees and shares with the connection)
└── Data source(s)   ← the schema, the properties, and the query endpoint live HERE
    └── Pages        ← one per day
```

**Consequences for this app, all of them load-bearing:**

- The setup flow is **three steps, not two**: find the database → resolve its data source → read the
  data source's properties. `Plan.md` M4.12's "list databases → pick the habit DB → map properties"
  is missing the middle one.
- What we persist alongside the property mapping is a **`data_source_id`**, not just a database id.
- `docs/notion-schema.md` describes "one database, `Habits`". That is still true of what the user
  sees; the API reaches its properties through a data source. A single-source database has exactly
  one, so in practice this is one extra call, not a new concept for the user.

---

## 2. Base URL, version and headers

| Fact | Value |
| --- | --- |
| Base URL | `https://api.notion.com` — "HTTPS is required for all API requests" |
| **Version to pin** | **`2026-03-11`** |
| Version header | `Notion-Version: 2026-03-11` — "must be included in all REST API requests" |
| Auth header | `Authorization: Bearer ntn_***` |
| Content type | `Content-Type: application/json` |

Omitting the version header is a `400 missing_version`, not a silent default.

**Every published version**, so a future session can see where our pin sits:

| Version | Date |
| --- | --- |
| **2026-03-11** | 11 Mar 2026 ← **pinned** |
| 2025-09-03 | 3 Sep 2025 ← introduced data sources |
| 2022-06-28 | 28 Jun 2022 |
| 2022-02-22 | 22 Feb 2022 |
| 2021-08-16 | 16 Aug 2021 |
| 2021-05-13 | 13 May 2021 |

### 2.1 What `2026-03-11` broke relative to `2025-09-03`

Three breaking changes. Only the first touches us:

1. **`archived` is removed in favour of `in_trash`**, "across all request parameters and response
   bodies".
2. `after` on Append Block Children is replaced by a `position` object. *(We append no blocks.)*
3. The `transcription` block type is renamed `meeting_notes`. *(Irrelevant.)*

`CLAUDE.md` §4 says to pin one version and change it "only as a deliberate, tested step — never
track latest". `2026-03-11` is that pin. **Do not bump it without re-reading this table.**

---

## 3. Auth — the terminology has moved, the model has not

`CLAUDE.md` §3.1 calls this an "internal integration token". **The live docs no longer use that
term.** What we want is now a **personal access token (PAT)**, prefix **`ntn_`**.

- Created at the **Developer portal → Personal access tokens → New token**; name it, select the
  Notion API capability, create.
- **The token is shown once.** "You won't be able to see it again."
- Integrations are now called **connections**.

**The substance of ADR-004 is unchanged**: a secret the user pastes in once, no OAuth, no backend,
one workspace. Only the words on the buttons changed — which matters for M4.12, because that screen
has to tell the user what to click, and telling them to find an "internal integration" would send
them looking for something that is not there.

### 3.1 The gotcha from `CLAUDE.md` §3.3 — confirmed, and worse than described

> "Before a connection can interact with your Notion workspace page(s), the page must be manually
> shared with the connection."

Sharing is done in Notion's own UI: page menu → **Add connections** → pick the connection.

**And the failure mode is exactly the misleading one §3.3 predicted.** From the status-code
reference, `404 object_not_found` means:

> the resource does not exist, **or** it has not been shared with the integration

The API gives you **no way to tell those apart**. A valid token plus an unshared database is a 404,
identical to a typo'd id. M4.12 must name this case explicitly and tell the user in plain words to go
and share the database — never surface it as "not found".

---

## 4. Endpoints we will use

| Purpose | Method + path |
| --- | --- |
| Find databases/data sources the token can see | `POST /v1/search` |
| List a database's data sources | `GET /v1/databases/{database_id}` → `data_sources[] {id, name}` |
| **Read the schema** (the habit properties) | `GET /v1/data_sources/{data_source_id}` |
| **Query pages** (the days) | `POST /v1/data_sources/{data_source_id}/query` |
| Create a page (a day we have no page for) | `POST /v1/pages` |
| Update a page (push check-offs) | `PATCH /v1/pages/{page_id}` |

**`POST /v1/search`** filters by object type:

```json
{ "filter": { "property": "object", "value": "data_source" }, "page_size": 100 }
```

`value` accepts `"page"` or `"data_source"`. Note it returns **data sources**, not databases — a
multi-source database yields several results.

**Creating a page** parents to the data source, not the database:

```json
{ "parent": { "type": "data_source_id", "data_source_id": "..." }, "properties": { } }
```

---

## 5. Pagination

Identical across every list endpoint.

| Direction | Field | Notes |
| --- | --- | --- |
| Request | `start_cursor` | omit for the first page |
| Request | `page_size` | **default 100, maximum 100** |
| Response | `object` | always `"list"` |
| Response | `results` | the array |
| Response | `has_more` | boolean |
| Response | `next_cursor` | "only appears when `has_more` is true" |

For `POST` endpoints these go in the **body**; for `GET`, the query string.

**One extra limit on the query endpoint:** it returns **at most 10,000 results**. Past that,
`has_more` goes *false* and `request_status` becomes `{"type": "incomplete", "incomplete_reason":
"query_result_limit_reached"}`.

> **Read that twice.** `has_more: false` does **not** mean "you have everything". A pull that
> trusts `has_more` alone will silently stop at 10,000 pages and believe it finished. Check
> `request_status.type` too. At one page per day this app needs ~27 years to reach it, so it is not
> a practical risk — but the incremental pull in §7 makes it unreachable anyway, and that is the
> better reason not to worry about it.

---

## 6. Rate limits and 429

| Fact | Value |
| --- | --- |
| Average rate | **~3 requests per second**, "with some bursts beyond the average allowed" |
| Burst size | **Not published.** Do not hardcode a guess — see §10. |
| Rate-limited | **`429`**, error code `rate_limited` |
| Retry delay | **`Retry-After` response header**, "an integer number of seconds" |
| Extra detail | `additional_data.rate_limit_reason`, e.g. `public_api_request_rate_limit` |
| Overload | **`529`**, code `service_overload` — a *different* condition, also worth backing off |

Request size limits, for the write path:

- rich text: **2,000 characters**; URLs: 2,000 characters
- payload: **500 KB**, and at most 1,000 block elements
- arrays: 100 blocks, 100 multi-select options, 100 relations

None of these bind us — this app writes checkboxes.

---

## 7. Incremental pull

`CLAUDE.md` §3.5: "Pull incrementally, filtered on last-edited time since the last successful sync.
Never re-fetch the entire database on every run."

The query endpoint supports a **timestamp filter**, which works whether or not the data source has a
matching property:

```json
{
  "filter": {
    "timestamp": "last_edited_time",
    "last_edited_time": { "after": "2026-08-22T09:00:00.000Z" }
  }
}
```

- Conditions: `after`, `before`, `equals`, `on_or_after`, `on_or_before`.
- "If a date string with a time is provided, then the comparison is done with **millisecond
  precision**."
- **"If no timezone is provided, then the timezone defaults to UTC."** Always send an explicit
  offset; do not rely on that default.

This is what `DayDao.latestRemoteEditMillis()` (built in M3.3, tested in M3.11) exists to feed.

---

## 8. Property shapes

Every page property value carries **`id`** and **`type`** alongside its value.

**Note the encoding.** Property ids arrive percent-encoded — real examples from the docs are
`"ZI%40W"` (`ZI@W`) and `"M%3BBw"` (`M;Bw`). Store them exactly as received; do not "clean" them.
`Title` properties are the exception: their id is the literal string `"title"`.

### The four types this app actually meets

Per `docs/notion-schema.md` §2.1 the user's data source has a Title, five Checkboxes, a Date, a
Formula, a Multi-select and two Numbers. We read the first four and ignore the rest.

```jsonc
// Title — unused by us (constant "Habits" on every page), but present on every response
{ "id": "title", "type": "title", "title": [ { "type": "text", "text": { "content": "…" }, "plain_text": "…" } ] }

// Checkbox — a habit, done or not
{ "id": "ZI%40W", "type": "checkbox", "checkbox": true }

// Date — THE DAY KEY. Date-only, null time zone, exactly as the export showed.
{ "id": "M%3BBw", "type": "date", "date": { "start": "2026-08-22", "end": null, "time_zone": null } }

// Formula — `Progress`. READ-ONLY.
{ "id": "CSoE", "type": "formula", "formula": { "type": "number", "number": 56 } }
```

A formula's inner `type` is one of `boolean` / `date` / `number` / `string` / `unsupported`.

### Read-only types

`CLAUDE.md` §3.2 asks which types cannot be written. **Formula is read-only**: *"You can't update
this value through the API."* Rollup is the same by nature. The user's `Progress` is a formula, so
it is **pull-only by definition** — and it is also a value we never want to write, since Notion
recomputes it from the checkboxes we do write.

M4.6 must prevent a read-only type being chosen as a write target in the mapping UI, rather than
letting it fail at sync time.

### The schema side

`GET /v1/data_sources/{id}` returns `properties` as a map **keyed by property name**, each value
carrying `id`, `name`, `type` and a type-specific config. So the name is both the map key and a
field — which is what makes the id/name distinction in §9 easy to get wrong.

---

## 9. ⭐ Property IDs — the question M3 left open, answered

`docs/notion-schema.md` §6.1 said: *"Notion database properties carry a stable `id` alongside their
name. **Confirm this in M4.1** and, if so, store the mapping against the ID."*

**Confirmed, explicitly, and with a bonus.** From the page-property-values reference:

> The `id` **"remains constant when the property name changes"**, and **"`id` may be used in place
> of name when creating or updating pages."**

Two facts, and the second is the one that makes this cheap:

1. **The id survives a rename**, so a mapping keyed on it survives too. Renaming `📖 Read` to
   `📖 Reading` in Notion becomes a non-event instead of looking exactly like a deleted habit.
2. **We can write using the id as the key**, so the push path never has to resolve id → current
   name. Had it been name-only, every write would first need a fresh schema fetch.

**Decision: key the property mapping on `id`, and write using `id`. Keep `name` for display only.**
This is what `docs/notion-schema.md` §6.1 asked for, now with evidence.

It does **not** weaken `CLAUDE.md` §3.4's "validate the mapping before every sync" — a property can
still be **deleted** or **retyped**, and both must pause sync with a clear error. It only removes
*rename* from the list of things that can break us.

---

## 10. Left open, deliberately

Neither blocks M4.2–M4.8. Both are recorded so nobody later assumes they were settled.

1. **The burst allowance is not published.** Only "an average of three requests per second, with
   some bursts beyond the average allowed". So M4.3's client-side throttle targets **3 req/s
   sustained** and treats the server's `Retry-After` as the authority, rather than modelling a
   burst bucket we would be inventing. Do not hardcode a burst number found in a blog post.
2. **`in_trash` vs `is_archived` on `PATCH /v1/pages`.** §2.1 says `archived` became `in_trash`
   "across all request parameters and response bodies", but the update-page and query pages also
   mention `is_archived`. **Resolve this at M4.5, against a real request, if it is ever needed.**
   It very likely never is: **ADR-036 established that v1 deletes and archives nothing.** `Plan.md`
   M4.5 lists "archive page" in the client surface — that is now optional, and should not be built
   speculatively.

---

## 11. Where the official docs contradicted themselves

Recorded because the wrong answer here would have shaped the whole mapping design.

**The update-page reference reads as though property keys must be names** — "Properties are
identified by name only, not by ID". **The page-property-values reference says the opposite**, and
explicitly: "`id` may be used in place of name when creating or updating pages."

**The property-values reference wins.** It is the page that specifically documents the property
object, it states the rule as a positive capability with its rationale (the id is stable across
renames), and a second source confirmed the keys "are the names or IDs of the property".

**Verify it with one real request at M4.5 anyway.** It is a single call to check, and §9's whole
design rests on it. If it turns out to be name-only, the mapping stays keyed on `id` regardless —
only the push path changes, resolving id → current name from the schema it already fetches.

---

## 12. Sources

All read 2026-08-22.

- [Versioning](https://developers.notion.com/reference/versioning)
- [Changes by version](https://developers.notion.com/reference/changes-by-version)
- [Upgrade guide — 2025-09-03 (data sources)](https://developers.notion.com/docs/upgrade-guide-2025-09-03)
- [Introduction / base URL](https://developers.notion.com/reference/intro)
- [Request limits](https://developers.notion.com/reference/request-limits)
- [Status codes](https://developers.notion.com/reference/status-codes)
- [Page property values](https://developers.notion.com/reference/page-property-values)
- [Property value object](https://developers.notion.com/reference/property-value-object)
- [Data source properties](https://developers.notion.com/reference/property-object)
- [Retrieve a data source](https://developers.notion.com/reference/retrieve-a-data-source)
- [Query a data source](https://developers.notion.com/reference/query-a-data-source)
- [Create a page](https://developers.notion.com/reference/post-page)
- [Update page](https://developers.notion.com/reference/patch-page)
- [Search](https://developers.notion.com/reference/post-search)
- [Authorization](https://developers.notion.com/docs/authorization)
- [Create an integration](https://developers.notion.com/docs/create-a-notion-integration)
