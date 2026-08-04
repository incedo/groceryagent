# Project Skills and Capabilities

**Status:** AGREED
**Last updated:** 2026-08-01

## 1. Purpose

Grocery Automate needs more than application development. Its quality depends on trustworthy grocery data, safe dietary filtering, comparable quantities and prices, reproducible recipe calculations, explainable recommendations, and reliable event synchronization.

This document describes the capabilities needed to build and operate the project. A capability can be owned by one specialist or shared across contributors; it does not imply that every area requires a separate person.

## 2. Core Engineering

### 2.1 Kotlin Multiplatform

Required knowledge:

- shared `commonMain` domain logic;
- coroutines, `Flow`, and kotlinx.serialization;
- platform boundaries using interfaces and `expect`/`actual` where appropriate;
- Gradle Kotlin DSL and version catalogs;
- deterministic multiplatform testing.

Expected outcome: product, offer, recipe, recommendation, comparison, preference, and event behavior is implemented once and compiled consistently for every configured target.

### 2.2 Compose Multiplatform

Required knowledge:

- unidirectional state flow and mostly stateless composables;
- adaptive compact, medium, and expanded layouts;
- phone, tablet, foldable, desktop, and web interaction patterns;
- accessible touch, pointer, keyboard, focus, and semantics behavior;
- observable projection-driven frontend state.

Expected outcome: one shared UI code path changes presentation across form factors without changing domain behavior.

### 2.3 Event-Driven Architecture

Required knowledge:

- commands, decision models, domain events, and invariants;
- event metadata, serialization, schema versions, and migrations;
- append-only event storage and idempotency;
- rebuildable projections and read models;
- correlation, causation, tracing, and failure diagnostics.

Expected outcome: domain changes follow the complete path:

```text
command
  -> decision model
  -> versioned domain event
  -> local or backend event store
  -> synchronization envelope
  -> projection/read model
  -> Flow/observable frontend state
  -> Compose UI
```

### 2.4 Offline-First Synchronization

Required knowledge:

- stable event and device IDs;
- idempotent push/pull protocols and cursors;
- duplicate and out-of-order delivery;
- retries, reconnect, and partial failure;
- offline edits, conflict resolution, and tombstones;
- frontend synchronization lifecycle state.

Expected outcome: a temporary connection loss or repeated event delivery cannot corrupt saved recipes, preferences, comparisons, or shopping lists.

### 2.5 Ktor Backend Development

Required knowledge:

- versioned JSON APIs;
- authentication and authorization;
- request validation and stable error envelopes;
- provider orchestration and rate limiting;
- structured logging, metrics, and tracing;
- keeping domain decisions outside route handlers.

Expected outcome: the backend acts as a secure adapter around shared domain behavior, event storage, projections, and external providers.

### 2.6 PostgreSQL and Persistence

Required knowledge:

- event-store and tag schemas;
- projection and read-model design;
- product matching and deduplication support;
- catalog, offer, recipe, and list indexing;
- transactions, concurrency, and safe migrations;
- backup, restore, and projection rebuilding.

Expected outcome: event history remains durable while query models stay fast, replaceable, and rebuildable.

## 3. Grocery-Domain Expertise

### 3.1 Product and Catalog Modelling

Required knowledge:

- GTIN, EAN, UPC, and barcode identity;
- product, brand, category, package, and variant modelling;
- canonical identity versus provider identity;
- retailer-specific listings and product deduplication;
- incomplete and conflicting catalog records.

Expected outcome: one canonical product may safely relate to multiple provider records and store-specific offers without merging incompatible variants.

### 3.2 Units, Prices, and Promotions

Required knowledge:

- mass, volume, count, and serving dimensions;
- safe amount conversion and rounding;
- money in integer minor units and ISO currency;
- normalized unit prices;
- multi-buy, loyalty, bundle, and validity-period conditions;
- regional tax, deposit, and packaging differences where applicable.

Expected outcome: comparisons use an explicit compatible basis while preserving original pack and promotion facts.

### 3.3 Nutrition, Ingredients, and Allergens

Required knowledge:

- ingredient and allergen taxonomies;
- nutrition per 100 g, per serving, and per recipe;
- dietary definitions and regional labeling differences;
- cross-contamination and may-contain statements;
- missing, uncertain, and conflicting provider data;
- the boundary between general grocery guidance and medical advice.

Expected outcome: safety-sensitive constraints fail closed, unknown data is clearly marked, and soft ranking never overrides an allergen conflict.

### 3.4 Recipe Modelling

Required knowledge:

- structured ingredients, quantities, and preparation states;
- serving scaling and rounding rules;
- ingredient-to-product resolution;
- substitutions and dietary compatibility;
- nutrition and cost estimation;
- leftovers, waste, and shopping-list consolidation.

Expected outcome: recipe calculations are reproducible and retain the source snapshots used for their estimates.

### 3.5 Recommendation Systems

Required knowledge:

- hard constraints versus soft preferences;
- deterministic weighted ranking;
- diversity, substitution, and repetition rules;
- freshness, completeness, and confidence weighting;
- explainable trade-offs and evidence;
- offline evaluation datasets and ranking-quality metrics;
- sponsored-result separation if monetization is introduced.

Expected outcome: every recommendation can explain why it qualified, why it ranked where it did, which evidence was used, and what uncertainty remains.

## 4. Data Integration and Quality

### 4.1 External Provider Integration

Required knowledge:

- product databases such as Open Food Facts;
- retailer catalogs, pricing, promotions, and availability;
- licensed recipe data sources;
- authentication, pagination, quotas, retries, and caching;
- mapping external payloads to canonical models;
- provider terms, attribution, redistribution, and retention rules.

Expected outcome: providers remain replaceable and their raw payload types never escape the integration boundary.

### 4.2 Data Quality Engineering

Required knowledge:

- provenance and observation timestamps;
- verification and confidence states;
- freshness windows per data type;
- conflicting-source resolution;
- impossible-value detection and ingestion diagnostics;
- deterministic, license-compatible test fixtures.

Expected outcome: the application distinguishes verified facts, estimates, missing values, conflicts, and stale observations instead of silently guessing.

## 5. Quality, Security, and Operations

### 5.1 Testing and Quality Engineering

Required knowledge:

- unit and property-based testing for quantities and conversions;
- command, event, decision-model, and projection tests;
- event serialization and schema-compatibility tests;
- sync retry, duplicate, ordering, offline, cursor, and reconnect scenarios;
- API contract and migration tests;
- Compose UI, accessibility, screenshot, and browser end-to-end testing;
- fixed clocks, seeded data, fake providers, and reproducible fixtures.

Expected outcome: the repository quality gates prove domain safety, event flow, synchronization, and adaptive frontend behavior without depending on live providers.

### 5.2 Security and Privacy

Required knowledge:

- OIDC, JWT, and Authorization Code Flow with PKCE;
- authorization and owner-scoped data access;
- secure token and provider-secret handling;
- location minimization;
- protection of allergies, dietary profiles, household information, and shopping history;
- logging, retention, deletion, and breach-aware design.

Expected outcome: private user data and provider credentials are minimized, correctly scoped, encrypted where appropriate, and absent from logs and source control.

### 5.3 DevOps and Release Engineering

Required knowledge:

- GitHub Actions and repository quality gates;
- Docker, Ktor, and PostgreSQL deployment;
- mobile, desktop, web, and backend release pipelines;
- observability and production diagnostics;
- backup, restore, rollback, and incident response;
- controlled dependency upgrades using the latest stable compatible releases.

Expected outcome: releases are reproducible, observable, reversible, and protected by relevant automated checks.

## 6. Product and Design Capabilities

### 6.1 Product and Grocery Experience Design

Required knowledge:

- translating shopping problems into bounded feature loops;
- preference onboarding without overwhelming users;
- comparison and recommendation transparency;
- recipe-to-list and store-selection workflows;
- regional grocery behavior and household needs;
- experimentation without compromising factual integrity or dietary safety.

Expected outcome: features solve recognizable shopping problems and expose enough context for users to make their own decisions.

### 6.2 UX, Accessibility, and Content Design

Required knowledge:

- responsive information hierarchy;
- accessible forms, tables, lists, and comparison views;
- plain-language explanations of scoring and uncertainty;
- visual distinction between facts, estimates, preferences, warnings, and sponsored content;
- localization of currencies, units, food terminology, and dietary labels.

Expected outcome: users can understand and operate the product across input methods, screen sizes, languages, and accessibility needs.

## 7. AI and Machine Learning

AI or ML expertise is not required for the first product loops. The initial recommendation system should be deterministic, testable, and explainable.

Later useful capabilities may include:

- semantic product and ingredient matching;
- personalized ranking trained from explicit feedback;
- recipe parsing into structured ingredients;
- natural-language explanation generation;
- anomaly and duplicate detection.

AI remains downstream from canonical facts and hard constraints. It must not become the authoritative source for allergens, ingredients, nutrition, prices, promotions, stock, or product identity.

## 8. Suggested Capability Ownership

In a small team, roles can overlap:

| Ownership area | Primary capabilities | Close collaboration |
|---|---|---|
| Shared application | Kotlin MPP, Compose, state flow | Domain, accessibility, testing |
| Domain and recommendations | Grocery modelling, ranking, recipes | Nutrition, data quality, product |
| Backend and synchronization | Ktor, events, PostgreSQL, offline sync | Client, security, operations |
| Provider data | Integrations, licensing, data quality | Domain, backend, product |
| Quality engineering | Automation, contracts, sync, UI tests | Every implementation owner |
| Product and UX | Grocery workflows, accessibility, content | Domain, client, data quality |
| Platform and operations | CI/CD, deployment, observability | Backend, security, quality |

No feature is complete through one discipline alone. A grocery comparison, for example, requires canonical modelling, unit normalization, provider freshness, accessible presentation, and deterministic tests.

## 9. Recommended Capability Order

Build capability in this order:

1. Kotlin Multiplatform architecture and deterministic testing.
2. Canonical grocery, money, quantity, and source models.
3. Commands, events, event storage, projections, and frontend state flow.
4. Product identity, catalog fixtures, and provider boundaries.
5. Normalized offer and product comparisons.
6. Structured recipes and shopping-list calculations.
7. Deterministic recommendation rules and explanations.
8. Live provider integrations and data-quality monitoring.
9. Authentication, PostgreSQL persistence, and multi-device sync.
10. AI-assisted matching or explanation after the factual pipeline is reliable.

This order makes the system testable and useful before it depends on live provider availability or opaque model behavior.

## 10. Definition of Capability Readiness

A capability is ready for production use when:

- its ownership and architectural boundary are clear;
- canonical contracts and relevant events are documented;
- deterministic tests cover normal, missing, conflicting, stale, and failure cases;
- event-driven behavior reaches the frontend projection;
- security, privacy, provider-license, and accessibility impacts are addressed;
- operational metrics and diagnostics exist where relevant;
- implementation documentation matches the deployed behavior.
