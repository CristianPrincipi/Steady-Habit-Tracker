package com.cristianprincipi.habits.data.remote.notion

/**
 * The constants the Notion client is built on, every one of them read from the live reference in
 * M4.1 and recorded in `docs/notion-api.md`.
 *
 * **`CLAUDE.md` §3.2: none of this may be written from memory.** They live in one object rather
 * than inline at their call sites so that a future session changing the pinned version has exactly
 * one place to change it — and so `grep 2026-03-11` finds the pin instead of finding nothing.
 */
object NotionApiSpec {
    /**
     * The trailing slash is load-bearing.
     *
     * Ktor resolves a request path against this the way a browser resolves a relative href: without
     * the slash, `data_sources/x` would replace `v1` rather than extend it, and every call would go
     * to `api.notion.com/data_sources/x`. Paths passed to the client therefore carry **no** leading
     * slash — one would reset to the host root and lose `/v1` just as surely.
     */
    const val BASE_URL = "https://api.notion.com/v1/"

    /**
     * The pinned API version — `docs/notion-api.md` §2, ADR-037.
     *
     * `CLAUDE.md` §4: pin one version and change it "only as a deliberate, tested step — never track
     * latest". §2.1 of that document lists what this version broke relative to the previous one.
     * **Do not bump this without re-reading that table.**
     */
    const val VERSION = "2026-03-11"

    /** Omitting this header is a `400 missing_version`, not a silent default (`notion-api.md` §2). */
    const val VERSION_HEADER = "Notion-Version"

    /** `docs/notion-api.md` §5: "default 100, maximum 100". Asking for more is a validation error. */
    const val MAX_PAGE_SIZE = 100
}
