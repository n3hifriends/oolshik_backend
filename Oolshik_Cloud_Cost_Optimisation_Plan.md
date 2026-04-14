Oolshik Phase 1.5 — Autoscaling Version

1. Goal

Keep the MVP simple, but upgrade it from:
• manual single-box
to
• basic autoscaling-ready

This version is for:
• pilot traffic
• one developer
• moderate cost increase
• better resilience than the current single-EC2 plan

2. Core change from current plan

Current plan:
• API + notification-worker on one EC2
• optional Kafka on same EC2
• manual scaling
• manual STT handling

Autoscaling version:
• API separated from workers
• API made stateless
• API placed behind a load balancer
• workers scaled independently
• STT handled asynchronously
• queue/job system becomes mandatory

3. Recommended autoscaling architecture

A. API layer

Use:
• AWS Auto Scaling Group
• 2 small EC2 instances minimum-capable design
• Application Load Balancer in front

Recommended shape:
• Launch template for API container host
• Min instances: 1
• Max instances: 2 or 3
• Scale on:
• CPU %
• memory [if available through CloudWatch agent]
• request count
• ALB target response time

Why:
• gives real autoscaling
• keeps API highly available enough for MVP/pilot
• avoids full Kubernetes/ECS complexity

B. Notification worker

Do not keep it inside the API box.

Use:
• separate worker service on:
• one separate EC2, or
• one container host

Scaling:
• start with 1 fixed worker
• later scale based on:
• queue depth
• pending jobs
• Kafka lag if Kafka is retained

C. STT worker

Keep STT completely separate from API.

For Phase 1.5:
• still CPU-first
• async only
• scale by job backlog

Recommended:
• one worker EC2 or one worker container host
• manual-to-automatic transition:
• min = 0 or 1 depending on chosen platform
• scale out when pending STT jobs cross threshold
• scale in when queue is drained

D. Database

Keep:
• Neon PostgreSQL/PostGIS

Reason:
• already serverless
• no change needed for API autoscaling
• shared DB endpoint works fine for multiple API instances

E. Storage

Keep:
• AWS S3

Reason:
• already shared and scalable
• no autoscaling change needed

F. Auth

Keep:
• Google Sign-In + app JWT

Requirement for autoscaling:
• API must stay stateless
• do not store session state in local memory
• if revocation/session tracking is needed, use shared storage later

4. Queue / eventing recommendation

For autoscaling, you now need a real shared async mechanism.

Best options:

Option 1 — DB-backed job table

Good if:
• MVP is still small
• Kafka is not deeply embedded
• you want lowest ops burden

Use for:
• STT jobs
• notification jobs
• retry status

This is my recommendation for your Phase 1.5 unless Kafka is already heavily wired.

Option 2 — Keep Kafka

Good if:
• your code already depends on Kafka
• notification-worker and STT are already using it
• removing Kafka now is expensive

But then:
• Kafka must move off the single API EC2
• at least run it separately
• otherwise autoscaling API while Kafka sits on one fragile box gives limited benefit

5. Minimum autoscaling rules

API autoscaling
• desired: 1
• min: 1
• max: 2
• scale out:
• CPU > 65% for 5 minutes
• scale in:
• CPU < 30% for 15 minutes

STT worker scaling
• if pending STT jobs > threshold, start/add worker
• if no pending jobs for 10–15 minutes, stop/remove worker

Notification worker
• keep fixed at 1 initially
• only scale later if backlog proves it

6. What must change in the app
   • API must be stateless
   • file storage must remain shared via S3
   • DB connections must be tuned for multiple API instances
   • health endpoints must be clean
   • config/secrets must be externalized
   • worker tasks must be idempotent
   • JWT validation must not depend on local memory

7. What stays the same
   • Neon DB
   • S3 storage
   • Google Sign-In
   • app JWT
   • CPU-first STT
   • no phone OTP in Phase 1
   • no production Phase 2 decision yet

8. Biggest trade-offs

Gains
• better uptime
• API can handle spikes better
• safer than one-EC2 setup
• cleaner path to later production design

Costs
• load balancer cost
• at least one always-on API instance
• higher EC2 cost than pure stop/start design
• more DevOps setup

9. Best practical version for you

My recommendation:
• API: AWS ASG + ALB, min 1 max 2
• notification-worker: separate small fixed worker
• STT: separate CPU worker, async, scale manually first then automate
• DB: Neon
• storage: S3
• queue: DB-backed job table unless Kafka is already deeply embedded
• auth: Google Sign-In + app JWT

10. Final verdict

Yes, this is the right autoscaling version of your current plan.

But the cleanest way is:
• autoscale only the API first
• keep workers simpler
• avoid forcing Kafka unless already deeply integrated

That gives you the maximum benefit with minimum architecture jump.

---

## Oolshik Phase 1.5 — Autoscaling Infra Diagram + Component List
```markdown
    <!-- prettier-ignore -->
    ```text
1.  Simple architecture diagram

                +----------------------+
                |   Mobile App Users   |
                +----------+-----------+
                           |
                           v
                +----------------------+
                | Google Sign-In       |
                | Identity Token       |
                +----------+-----------+
                           |
                           v
                +----------------------+
                | AWS Application      |
                | Load Balancer (ALB)  |
                +----------+-----------+
                           |
                +----------+----------+
                |                     |
                v                     v
      +------------------+   +------------------+
      | API EC2 Instance |   | API EC2 Instance |
      | Spring Boot API  |   | Spring Boot API  |
      | JWT issue/verify |   | JWT issue/verify |
      +--------+---------+   +--------+---------+
               \                   /
                \                 /
                 +---------------+
                         |
                         v
              +------------------------+
              | Neon PostgreSQL        |
              | + PostGIS              |
              | users / tasks / jobs   |
              +------------------------+
                         |
                         v
              +------------------------+
              | AWS S3                 |
              | audio / files          |
              +------------------------+

                         |
                         v
              +------------------------+
              | Notification Worker    |
              | separate EC2 / process |
              +------------------------+

                         |
                         v
              +------------------------+
              | STT Worker             |
              | separate CPU worker    |
              | async only             |
              +------------------------+

    ```
    ```



2.  Component list

A. API layer
• Provider: AWS
• Service: EC2 Auto Scaling Group + Application Load Balancer
• App: Spring Boot API
• Purpose: serve mobile APIs, verify Google token, issue app JWT, handle business logic
• Scaling rule: min 1, max 2 initially
• Important requirement: API must be stateless

B. Load balancer
• Provider: AWS
• Service: ALB
• Purpose: route traffic to multiple API EC2 instances
• Health check: /actuator/health
• Needed for autoscaling: yes

C. Database
• Provider: Neon
• Service: Serverless PostgreSQL + PostGIS
• Purpose: core relational data
• Shared across all API instances: yes
• Good for Phase 1.5: yes

D. Storage
• Provider: AWS
• Service: S3
• Purpose: shared file/audio storage
• Why important for autoscaling: all API/worker nodes use the same object store

E. Auth
• Identity source: Google Sign-In
• App auth: app-issued JWT
• Purpose: keep auth simple for MVP
• Scaling note: JWT/session logic must not depend on local instance memory

F. Notification worker
• Provider: AWS
• Service: separate small EC2 or separate worker process host
• Purpose: background notification processing
• Scaling: fixed at 1 initially

G. STT worker
• Provider: AWS
• Service: separate CPU-only EC2
• Purpose: async speech-to-text
• Scaling: manual first, then backlog-triggered
• Important: keep separate from API

H. Async job system
• Preferred for Phase 1.5: DB-backed job table
• Alternative: Kafka only if already deeply integrated
• Purpose: coordinate notification/STT jobs without tying them to API request time

3. Recommended implementation sizing

API autoscaling group
• Instance type: start with t3.small only if API is lightweight
• better safe option: t3.medium
• Min: 1
• Max: 2
• Desired: 1

Notification worker
• one small EC2 or colocated on a separate lightweight box
• fixed count = 1

STT worker
• one CPU worker only
• no GPU in this phase

4. Recommended scaling rules

API
• scale out when:
• CPU > 65% for 5 minutes
• scale in when:
• CPU < 30% for 15 minutes

STT worker
• start worker when pending STT jobs cross threshold
• stop worker when queue stays empty for 10–15 minutes

Notification worker
• keep fixed at 1 for now

5. App design requirements for this architecture
   • API must be stateless
   • JWT validation cannot depend on instance-local memory
   • files must go to S3, not local disk
   • jobs must be retry-safe / idempotent
   • DB connection pool must be tuned for multiple API nodes
   • health endpoint must reliably report readiness

6. Best Phase 1.5 build order
   1. Make API stateless
   2. Add ALB + ASG for API
   3. Move notification-worker out of API box
   4. Keep STT separate and CPU-only
   5. Use DB-backed jobs unless Kafka is unavoidable
   6. Add CloudWatch alarms and health checks

7. Final short recommendation

Best practical autoscaling MVP:
• ALB
• ASG with 1–2 API EC2 instances
• Neon DB
• S3 storage
• Google Sign-In + app JWT
• separate notification worker
• separate CPU STT worker
• DB job table instead of Kafka unless Kafka is already tightly coupled

If you want, next I’ll convert this into a Terraform-style component checklist or a step-by-step setup order.
