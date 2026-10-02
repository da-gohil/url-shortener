# URL Shortener

A URL shortener built with Spring Boot, Spring Security, Spring Data JPA, Thymeleaf and PostgreSQL. It turns long links into short `/s/{key}` links. Signed-in users can manage their links, and admins get a moderation dashboard. The app is also hardened against the usual ways link shorteners get abused.

## Features

### For everyone
- Shorten any public `http(s)` link. Links created without signing in are public and expire after 30 days. The new short link appears straight away with a copy button.
- The home page lists public links, with pagination and a copy button on each one.
- An **About** page explains how the service works and what each kind of account can do. Its numbers (guest expiry, guest rate limit) are read from configuration, so they always match the app.
- Short links redirect to their destination, and each visit counts a click. Clicks are counted with an atomic database update, so simultaneous visits aren't lost.

### For signed-in users
- **Registration and login.** Passwords are hashed with BCrypt. A new account is signed in straight away and lands on My URLs, with a fresh session that an admin can end like any other. Every sign-up is recorded in the audit log.
- **Private links** that only their owner can open. For anyone else, a private link returns the same 404 as an unknown key, so it can't be discovered by guessing.
- **Custom expiry** in days, or never.
- **My URLs page:**
  - Stats: links, total clicks, active, expired.
  - Search by short key or destination; filter by visibility and status; sort by newest, oldest, most clicks or soonest expiry. Filters are kept in the URL, so paging and bookmarks keep them.
  - Select-all and bulk delete, plus a delete button on each row.
  - **Editing a link:** make it public or private, and keep, remove or reset its expiry. The destination is fixed, so a link you've already shared keeps going where it went.

### For admins
Admins can do everything a user can (role hierarchy `ADMIN > USER`), plus:

| Tab | What it does |
|---|---|
| **Overview** | Site-wide totals, a chart of links created per day over the last 14 days (with a table view), and the most-clicked links. |
| **Links** | Every link, with the same search, filters and sorting, plus a filter by owner (one user or guests). Edit, delete, and **disable/enable**. A disabled link stops redirecting but keeps its record, clicks and short key. |
| **Users** | Search accounts, see each user's link count, **make/remove admin**, and **disable/enable** accounts. Changes take effect immediately, because the user's live session is ended. An admin can't demote or disable themselves, so there's always at least one admin. |
| **Audit log** | Who edited, deleted, disabled or changed the role of what, and when. Each entry is written in the same transaction as the change it records. |

## Security

- **Authorization in two layers.** URL rules in the security filter chain, and `@PreAuthorize` on the service methods, with ownership checks for editing and deleting links.
- **SSRF protection.** Before a link is accepted, the server checks that the URL exists. Every host it connects to, including each redirect hop (followed by hand, up to 5), must resolve to a public address. Loopback, private, link-local (including the cloud metadata address `169.254.169.254`), carrier-grade NAT and IPv6 private ranges are all refused.
- **Unsafe destinations are refused:**
  - URLs with embedded credentials (`https://yourbank.com@evil.example`).
  - Links back to this site.
  - A configurable host blocklist.
  - Sites flagged by Google Safe Browsing, if you configure an API key.
- **Rate limits:**
  - Link creation: 10 attempts an hour per IP for guests, 100 per account for signed-in users.
  - Registration: 5 attempts an hour per IP. Only attempts that pass form validation count, so typos don't use the allowance up.
  - Sign-in: locked for 15 minutes after 5 failures for one email or 20 from one IP. The lockout message doesn't reveal whether the account exists.
- **Password rules:**
  - 8 characters to 72 bytes (BCrypt's real limit, since emoji and accented letters take several bytes each).
  - No common passwords, and nothing containing your name or email.
- **Security headers:** a strict Content-Security-Policy (scripts only from this site; the page can't be framed), plus Referrer-Policy and Permissions-Policy. CSRF protection is on for every form.

## Frontend

The UI is rendered on the server with Thymeleaf: one layout, with shared fragments for the URL table, filter bar, pager, admin tabs and alerts.

- **Works without JavaScript.** Every form submits normally. `app.js` only adds copy buttons, select-all and delete confirmations.
- **Responsive.** Every page fits a 390px phone screen; wide tables scroll inside their own box instead of widening the page.
- **Accessible:**
  - A "Skip to main content" link and a `<main>` landmark.
  - One `<h1>` per page.
  - Form errors mark the field invalid and are linked to it with `aria-describedby`.
  - The pager and every control can be used with a keyboard; buttons repeated on each row say which row they act on.
  - The admin chart has keyboard-reachable tooltips and a table view.
- **Safe output.** All values are HTML-escaped (no `th:utext` or inline expressions), and there are no inline scripts, which lets the strict Content-Security-Policy block scripts from anywhere else.
- Browser tab titles name the page and the site, e.g. "About · URL Shortener".

## Tech stack

Java 25 · Spring Boot 4.1 · Spring Security 7 · Spring Data JPA (Hibernate) · Thymeleaf with Layout Dialect · Bootstrap 5 · PostgreSQL 17 · Flyway · JUnit 5, Mockito and MockMvc

## Getting started

**You need:** JDK 25 and Docker.

```bash
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL container from `compose.yaml` automatically and stops it when the app exits. Flyway creates the schema and loads sample data. Then open <http://localhost:8080>.

### Sample accounts (local development only)

| Email | Password | Role |
|---|---|---|
| `admin@example.com` | `password` | Admin |
| `john.doe@example.com` | `password` | User |
| `jane.smith@example.com` | `password` | User |

These come from the `V2`/`V3` migrations. Don't run those migrations against a real environment.

## Running the tests

The integration tests use the local PostgreSQL database, so start it first:

```bash
docker compose up -d
./mvnw test
```

Tests that write data roll back or clean up after themselves. A few tests of the URL checker call real websites and are skipped by default. Run them with:

```bash
./mvnw test -Dtest=UrlExistenceValidatorTest -Dnetwork.tests=true
```

## Configuration

All settings live in `src/main/resources/application.properties`, and any of them can be overridden with an environment variable (e.g. `APP_PAGE_SIZE=20`).

| Setting | Default | Meaning |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Used to build short links, and to spot links back to this site. |
| `app.default-expiry-in-days` | `30` | Expiry for links created without signing in. |
| `app.validate-original-url` | `true` | Check that a destination exists before shortening it. |
| `app.page-size` | `10` | Rows per page in every listing. |
| `app.url-safety.blocked-hosts` | *(empty)* | Comma-separated hosts (and their subdomains) that can't be shortened. |
| `app.url-safety.safe-browsing-api-key` | *(empty)* | Google Safe Browsing v4 key; blank turns the lookup off. Set it via `APP_URL_SAFETY_SAFE_BROWSING_API_KEY` rather than committing it. |
| `app.rate-limit.anonymous-links-per-hour` | `10` | Link-creation attempts per IP when signed out. |
| `app.rate-limit.user-links-per-hour` | `100` | Link-creation attempts per signed-in user (admins are exempt). |
| `app.rate-limit.login-failures-per-account` | `5` | Failed sign-ins for one email before it's locked. |
| `app.rate-limit.login-failures-per-ip` | `20` | Failed sign-ins from one IP before it's locked. |
| `app.rate-limit.login-lockout-minutes` | `15` | How long a lock lasts. |
| `app.rate-limit.registrations-per-ip-per-hour` | `5` | Registration attempts per IP that pass form validation. |

## Project layout

```
src/main/java/com/darshangohil/urlshortener/
├── config/           security filter chain, method security and role hierarchy
├── domain/
│   ├── entities/     JPA entities: ShortUrl, User, AuditEvent
│   ├── models/       DTOs, commands, filters, stats records
│   ├── repository/   Spring Data repositories and JPA Specifications
│   └── services/     business logic, authorization rules, URL safety, audit log
└── web/
    ├── controllers/  Home / My URLs, auth, admin
    ├── security/     rate limiting and sign-in throttling
    └── dtos/         form objects
src/main/resources/
├── db/migration/     Flyway migrations V1–V6
├── templates/        Thymeleaf pages (layout.html decorates the rest) and shared fragments
└── static/           styles.css, app.js (copy, select-all, confirmations)
```

## Known limitations

This is a portfolio project, and a few things would need more work for production:

- **Single instance only.** Rate limits and the session registry (used to sign users out when an admin changes their account) live in memory. Running several instances would need a shared store such as Redis or Spring Session.
- **Behind a reverse proxy,** set `server.forward-headers-strategy=native` so rate limits see real client IPs.
- **DNS rebinding.** A host can resolve to a public address during the SSRF check and a private one when the connection is made. Closing that gap needs an outbound proxy.
- **Per-account lockout can be abused.** Someone who knows an email address can lock that account out for 15 minutes.
- **Email addresses aren't verified.** A new account works immediately, so anyone can register with an address they don't own. Verification would need an SMTP server and confirmation tokens.
- **Registration says when an email is taken.** That tells a visitor the address has an account (sign-in doesn't). Without email verification there's no other way to tell someone why they can't register; the per-IP limit caps how fast addresses can be probed.
- **Common passwords come from a short bundled list.** A production system would check a breached-password service instead.
