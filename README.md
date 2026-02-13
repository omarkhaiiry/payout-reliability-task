# Payouts Reliability Task

**Goal:** Eliminate "Double Payouts" caused by network timeouts and retries.

## Core Solution
1.  **Database Constraint:** `UNIQUE(client_id, idempotency_key)` in Postgres. This is the **primary safety guarantee**.
2.  **Transparent Recovery:** 
    *   **Check-Then-Act**: Service checks for existing payout first.
    *   **Race Condition Handling**: If concurrent requests bypass the check, the DB constraint triggers a `DataIntegrityViolationException`.
    *   **Recovery**: Service catches the exception, fetches the existing record, and returns `200 OK` (Masking the internal conflict).

## Execution Flow
```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as PayoutController
    participant Service as PayoutService
    participant DB as Database (Postgres)
    participant Queue as PayoutQueueSender (SQS)

    Note over Client, API: 🚀 SCENARIO 1: New Request (Happy Path)
    Client->>API: POST /payouts (Key: A1)
    API->>Service: createPayout(Key: A1)
    
    %% 1. Proactive Optimization Check
    Service->>DB: findExisting(Key: A1)
    DB-->>Service: (Empty)
    
    %% 2. Attempt Save
    Service->>DB: INSERT Payout(Key: A1)
    DB-->>Service: Success (ID: 100)
    
    %% 3. Asynchronous Handoff (The Integration)
    Service->>Queue: send(Payout ID: 100)
    Note right of Queue: [Log] 📨 Sending to SQS...
    
    Service-->>API: Payout (Created=true)
    API-->>Client: 201 Created ✅

    %% SCENARIO 2 starts here
    Note over Client, API: ⚡ SCENARIO 2: Race Condition / Retry
    Client->>API: POST /payouts (Key: A1)
    API->>Service: createPayout(Key: A1)
    
    %% 1. Proactive Check (Might miss in high concurrency)
    Service->>DB: findExisting(Key: A1)
    DB-->>Service: (Empty) -- Race Condition Window!
    
    %% 2. Attempt Save (Fails because ID:100 exists now)
    Service->>DB: INSERT Payout(Key: A1)
    Note right of DB: ❌ UNIQUE CONSTRAINT VIOLATION!
    DB-->>Service: Throw DataIntegrityViolationException
    
    %% 3. Reactive Recovery (The "Catch" Block)
    Note over Service: Catch Exception -> Recover
    Service->>DB: findExisting(Key: A1)
    DB-->>Service: Return Payout (ID: 100)
    
    %% Notice: We do NOT send to Queue again (Status is already PENDING)
    
    Service-->>API: Payout (Created=false)
    API-->>Client: 200 OK (Recovered) ♻️
```

## Infrastructure (Terraform)
*   **SQS Queue**: Decouples payout processing.
*   **DLQ (Dead Letter Queue)**: Captures messages after **5 retries** (Redrive Policy).
*   **CloudWatch**: Alarm on `ApproximateAgeOfOldestMessage` (> 300s) for consumer lag.

## Verification
*   **Unit Tests**: Mock processing logic.
*   **Integration Tests**:
    *   `PayoutIntegrationTest`: Verify happy path & 200 OK on retry.
    *   `RaceConditionIT`: Simulate `DataIntegrityViolationException` recovery using Mockito.

## Quick Start:
- Run the Application
- H2 DB is already configured to make testing easier.
- Import postman_collection.json to test the Idempotency logic instantly.