---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - api
---

# API приватності (user-service)

Ендпоінти права на доступ, права на забуття і згод. Поведінкові контракти — у
[SDD, розділ 5.1](../architecture/sdd.md#51-синхронні-rest-контракти-через-gateway-якщо-не-вказано-інше);
компонент — [SDD 3.8](../architecture/sdd.md#38-user-service).

| Ендпоінт (через gateway) | Хто | Що |
|---|---|---|
| `POST /api/users` | будь-хто | реєстрація (надсилає лист верифікації) |
| `POST /api/users/password-reset` | будь-хто | посилання на скидання; однакова відповідь, чи існує обліковий запис, чи ні |
| `GET /api/users/{id}/personal-data` | сам користувач або `PRIVACY_OFFICER` | версійований експорт усіх персональних даних з усіх джерел |
| `POST /api/users/{id}/anonymize` | сам користувач або `PRIVACY_OFFICER` | стирання: видалити / анонімізувати / зберегти за правилом, включно із замовленнями |
| `GET /api/users/{id}/consents` | сам користувач | стан згод за метою та їх історія |
| `POST /api/users/{id}/consents/{purpose}/grant` · `/revoke` | сам користувач | `MARKETING_EMAIL`, `OPTIONAL_ANALYTICS` |
| `POST /api/marketing/emails` | `MARKETING` | маркетинговий лист, лише якщо дозволяє політика згод |
| `POST /api/analytics/events` | сам користувач | опційна аналітика, записується лише зі згодою |

Автентифікація — HTTP Basic, ім'я користувача — e-mail. Чужий `id` отримує той самий 404, що й неіснуючий.

## Демонстраційні облікові записи

Синтетичні акаунти з seed-міграції `V2__seed_demo_users.sql`; паролі лише для демонстрації.

| Обліковий запис | Пароль | Роль |
|---|---|---|
| `alice@example.test` | `Alice-Demo-2026` | CUSTOMER |
| `bob@example.test` | `Bob-Demo-2026` | CUSTOMER |
| `dpo@example.test` | `Dpo-Demo-2026` | PRIVACY_OFFICER |
| `marketing@example.test` | `Marketing-Demo-2026` | MARKETING |
| `erin@example.test` | `Erin-Demo-2026` | CUSTOMER (для демонстрації стирання) |

```bash
./scripts/privacy-demo.sh reset     # відновити seed-дані user-service
./scripts/privacy-demo.sh 1         # один крок демонстрації в терміналі (1-4)
./scripts/privacy-demo.sh           # reset + усі кроки, протокол у docs/privacy-demo.md
```

Ті самі запити для HTTP-клієнта IntelliJ — у `user-service/privacy-demo.http`. Службові ендпоінти `/internal/**`
order-service не маршрутизуються gateway і вимагають спільного `INTERNAL_API_TOKEN`. Секрети з лабораторними
значеннями за замовчуванням: `INTERNAL_API_TOKEN`, `PRIVACY_PSEUDONYMIZATION_KEY`, `EMAIL_PROVIDER_API_KEY`.
