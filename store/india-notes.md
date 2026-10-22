# Publishing from India - checklist

Researched 22 Oct 2026 from third-party summaries; Google's own Play Console Help is the authority, so re-check each item there before submitting.

## Google Play developer account
* **Registration fee:** one-time US$25, paid by card (enable international payments on the card if needed).
* **Identity verification:** Play verifies developers (legal name, address, phone, email and a government ID for personal accounts). Use details that match your ID; the name shown on the store listing is your developer name.
* **New personal accounts (created after 13 Nov 2023):** Google requires a **closed test with at least 12 testers opted in for 14 continuous days** before you can apply for production access. Organization accounts (registered with a D-U-N-S number) are exempt. Plan for this: recruit 12+ friends/family with Android TVs (or use the TV app on any Android TV), share the opt-in link, keep them opted in for 14 days, and use the app. Third-party sources say testers must show real usage and that dropping below 12 can reset the clock - unconfirmed.
* **Contact email:** Play shows a developer email publicly on the listing; use a dedicated address rather than a personal one.

## Data protection (India)
* **DPDP Act 2023 / DPDP Rules 2025:** notified Nov 2025 and phasing in. They govern *processing of personal data by a "Data Fiduciary"*. EthanConnect has no backend, no accounts, no analytics and does not transmit personal data to the developer, so there is little to comply with - the privacy policy states this and gives a contact for questions (see "Users in India" in `docs/privacy.md`).
* If you later add analytics, crash reporting, ads, sign-in or cloud features, you must (a) give a clear notice and obtain consent, (b) support access/correction/erasure, (c) name a grievance contact, (d) update the Play Data safety form and the privacy policy.
* Children: the app is not directed at children; keep the target audience 13+/general.

## Store and tax
* A free app with no in-app purchases needs only a basic payments profile; no GST registration is required to publish a free app. If you ever sell the app or subscriptions, set up the payments profile, Indian tax details (PAN) and consider GST.
* Language: English is fine; adding Hindi strings later is optional.
* Device approvals: Android TV apps go through the TV quality review - the app must be fully usable with a D-pad remote (it is) and have a TV banner (it has).

## Other
* No India-specific restriction on screen-casting/launcher apps was found, but keep the listing accurate: say plainly that a free companion Windows app is required.
