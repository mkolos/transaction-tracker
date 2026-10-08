# Transaction Tracker - Project Brief & Learning Goals

## Context
- **Goal:** Learning backend engineering + portfolio project
- **Timeline:** 8 weeks
- **Real use:** Expense tracking + splitting with others
- **Experience:** 3y IC + 4y EM (returning to hands-on coding)
- **Focus:** Learn async systems, database design, API patterns

## MVP Scope

### Features (In Scope)
1. **CSV Upload**
   - Accept multiple CSV files
   - Store temporarily
   - Support multiple bank formats

2. **Async Processing**
   - Job: Parse CSV → normalize format
   - Job: Create Transaction rows from normalized data
   - Job: Apply rule-based categorization
   - Store in database

3. **Categorization**
   - Rule-based engine (check description, amount contains X → category Y)
   - User can update category via API after parsing
   - Extensible (users can add rules)

4. **Transaction CRUD**
   - GET /transactions (list, filterable)
   - GET /transactions/{id}
   - PUT /transactions/{id}/category (update)
   - DELETE /transactions/{id}

5. **Export**
   - GET /export → CSV file
   - Respect filters (date range, category, etc.)

6. **Multi-Currency**
   - Store original currency with amount
   - Display / filter by currency

### Out of Scope (MVP)
- User accounts / auth
- Multi-user support (single user for MVP)
- Advanced ML categorization
- React frontend
- Bank API integrations
- Splitting/settlement logic (v2)
- Budget tracking (v2)

## Tech Stack
- **Language:** Java 25+
- **Framework:** Spring Boot 4.x
- **Database:** PostgreSQL
- **Async:** Spring Task Scheduler + message queue (Spring's @Async)
- **Testing:** JUnit + Mockito + TestContainers
- **Build:** Gradle
- **Monitoring:** Simple logging + optional Prometheus
- **Deployment:** Docker

## Architecture Decisions (To Be Designed)

### 1. Async Job Architecture
**Question:** How should CSV processing flow?
- One big job? (Upload → parse → categorize → store)
- Multiple stages? (Separate jobs for each step)
- Error recovery? (Retry logic, dead letter queue)

### 2. Categorization Rule Engine
**Question:** How do we represent and apply rules?
- Format: `{"pattern": "Starbucks", "category": "Food", "type": "CONTAINS"}`
- Storage: Database table or configuration file?
- Extensibility: Users can add rules? Or just code updates?

### 3. CSV Format Normalization
**Question:** How do we handle different bank formats?
- Hard-coded parsers for each bank?
- Configuration-based approach?
- Just require standardized input for MVP?

### 4. Database Schema
**Question:** Minimum schema to support MVP?
- Transactions table (id, date, amount, currency, description, category)
- Categories table (id, name, rules?)
- Rules table (if rule-based categorization)
- Any indexes needed?

### 5. Error Handling
**Question:** What can go wrong? How do we recover?
- Malformed CSV
- Missing required columns
- Categorization failure
- Database errors during import
- Duplicate transactions

## Learning Priorities (In Order)
1. **Async job processing** - CSV parsing, failure recovery, error handling
2. **Database design** - Schema, indexes, transactions
3. **Extensible architecture** - Rule engine, plugin pattern
4. **API design** - Clear contracts, error responses
5. **Testing strategy** - Unit + integration + end-to-end

## Key Metrics for Success
- ✅ Can upload multiple CSVs and parse them correctly
- ✅ Categorization works for 80%+ of transactions
- ✅ Can update categories and export modified CSV
- ✅ API is clear and documented
- ✅ Tests coverage above 90%
- ✅ Async job handles failures gracefully

## Design Phase: Questions for Claude

Before coding, I want to understand:

1. **Why PostgreSQL over other options?** (For expense tracking, what matters?)
2. **What's the minimal schema?** (What's essential, what can wait for v2?)
3. **How should the async job handle partial failures?** (Parsed CSV but categorization fails)
4. **Should rules be code or configuration?** (Tradeoffs?)
5. **What should the API error responses look like?** (For malformed CSV, etc.)

## Next Steps
1. Refine schema and API design with Claude
2. Break into weekly tasks
3. Start coding (Week 1: project setup, schema, basic API)