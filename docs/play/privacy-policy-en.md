# Privacy Policy — Potty Pause / Pause Pipi

Last updated: 2026-11-22

**Publisher and data controller:** Virtuel 3D Studio
**Contact (privacy, access, deletion):** 14735340.canada.inc@gmail.com

## 1. Who the app is for
Potty Pause is a tool for **parents**: it shows a periodic "bathroom break" reminder on a child's tablet. Only a parent (adult) can create an account and manage the app. **The child has no account** and enters no information or forms. The app is not meant to be used independently by children.

## 2. Two modes
- **No account (standalone mode)**: the app runs entirely on the tablet. Settings stay on the device. No data is sent to our servers.
- **Remote control (optional)**: the parent signs in with Google and pairs one or more tablets to adjust the reminder remotely. Only this mode sends data to the cloud (section 3).

## 3. Data collected (remote-control mode only)
**Parent account** (Google sign-in)
- Account identifier (Firebase uid), Google account e-mail address, device language (fr/en), creation date.
- We do not receive your Google password.

**Tablet** (anonymous sign-in, no e-mail or name)
- Anonymous identifier (uid), creation date.
- Tablet name: the **child's first name or nickname, entered by the parent** (max 40 characters). We recommend a first name or nickname, not a full name.
- Settings chosen by the parent: interval, lock duration, custom message (max 200 characters), theme, animation, character, language, reminder on/paused, revision number.
- Technical status: last-seen date (about every 15 minutes), app version, Android version (API level), date and number of the last applied revision.
- Technical alerts: overlay permission denied, reminder stopped, battery optimization active, notifications denied, tablet offline for a long time.
- During pairing: a temporary 8-digit code (10 minutes) and, for a pending request (5 minutes), the requesting parent's identifier and e-mail (shown on the tablet for confirmation).

**What we do NOT collect**: location, contacts, photos, microphone, camera, screen content, apps used, screen time, browsing history, advertising ID. Any image chosen for the "photo" theme stays on the tablet.

**The parent PIN is never sent to the cloud.** It is stored only on the tablet as a salted PBKDF2 hash and is used to protect local settings and to confirm a pairing.

Like any online service, Google/Firebase technically processes IP addresses and connection logs to run and secure the service.

## 4. Purposes and legal basis
- Operating the remote control (accounts, pairing, settings, status): performance of the service requested by the parent.
- Security (limiting abuse, expiring codes): legitimate interest.
- Consent: the parent accepts this policy when creating the account.

We do not use this data for advertising, profiling or automated decision-making.

## 5. Recipients
No sharing, sale or transfer to third parties. Sole processor: **Google (Firebase Authentication and Cloud Firestore)**. Firestore data is hosted in **Europe (eur3 multi-region)**; Google may still process some technical data (e.g. authentication) outside Europe under its data processing terms. **No advertising, no analytics SDK, no third-party crash reporting** in the app.

## 6. Retention
- Parent account and its tablets' documents: kept **until the parent deletes them** (deleting a tablet or the account, section 7).
- Pairing codes: 10-minute lifetime; unusable after expiry and deleted by the tablet once paired.
- Pairing requests: valid for 5 minutes.
- After deletion, Google may keep technical backup copies for a limited time before final erasure.
- A tablet leaving remote-control mode deletes its cloud document. Its anonymous authentication identifier (no personal data) may remain; we delete it on request.

## 7. Your rights and deletion
Depending on where you live (GDPR in the EU/EEA; PIPEDA and Quebec private-sector privacy law in Canada; other local laws), you may request access, correction, erasure, restriction, portability, object to processing and withdraw consent. You may also complain to your authority (CNIL, Commission d'accès à l'information du Québec, Office of the Privacy Commissioner of Canada, etc.).

- **Delete your data in the app**: parent mode > "Delete my account and data". This deletes all your paired tablets, your profile and your sign-in account. A new Google sign-in may be required. The affected tablets become "unpaired".
- **Delete a single tablet**: unpair it in parent mode, or from the tablet (behind the parent code).
- **By e-mail**: 14735340.canada.inc@gmail.com, from the account's address; we reply within 30 days.

Uninstalling the app does not delete cloud data: use one of the methods above.

## 8. Children (COPPA and equivalents)
The app is directed at parents. No account is created for a child and we collect no information directly from a child. The only child-related information (first name or nickname, settings) is entered by the parent, the adult account holder, who consents on the child's behalf. A parent can review, change or delete it at any time (section 7). If you believe a child has given us data, write to us and we will delete it.

## 9. Security
Encrypted exchanges (HTTPS/TLS) with Firebase; data access limited by Firestore rules: a parent sees only their own tablets; a tablet writes only its own status; a parent becomes manager of a tablet only after confirmation on the tablet with its code. No system is infallible, so we cannot guarantee absolute security.

## 10. Android permissions
Display over other apps (the reminder), foreground service with a notification (keep the reminder running), notifications, start on device boot, Internet (remote control). These permissions are not used to collect data.

## 11. Changes
Material changes will be announced in the app and by the date above.
