**Temporal + spring boot integration test sample project**



Sample workflow consist of:
1. Order creation from customer request
2. Stock quantity reservation
3. Charge for reserved items
4. Order shipping
5. Optional chargeback on partial shipping

Supplemental workflows:
1. Stock resupplying
2. Stock stealing

Two workflow implementations through same service methods provided:
1. Trivial with DB concurrency exceptions retry
2. Temporal with saga compensation

Temporal server and Postgresql started in testcontainers