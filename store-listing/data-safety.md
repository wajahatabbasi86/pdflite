# Play Console — Data Safety answers

Copy these into **Play Console → App content → Data safety**. Every answer here is backed by
`privacy-policy.html`; if one changes, change the other in the same commit.

## Overview
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (AdMob, UMP and Play Billing all use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No** — TrenDoc keeps no server-side data. Advertising ID is reset/deleted in Android settings |

## Data types
| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| **Device or other IDs** (advertising ID) | Yes | Yes — Google AdMob | Advertising or marketing | No (required for ads) |
| **App interactions** (ad impressions/taps, via AdMob) | Yes | Yes — Google AdMob | Advertising or marketing, Analytics | No |
| **Diagnostics** (AdMob SDK crash/performance data) | Yes | Yes — Google AdMob | Advertising or marketing | No |
| **Purchase history** (1-day Remove Ads, optional donations) | Yes (via Google Play Billing) | No | App functionality | Yes |
| Files and docs | **No** — processed on-device only | No | — | — |
| Photos and videos | **No** — camera photos stay in app-private cache | No | — | — |
| Personal info, location, contacts, messages, audio, health, financial info | **No** | No | — | — |

Notes:
- Data processed only on-device and never sent off it is not "collected" under Play's definition,
  which is why files, photos, recents and appearance settings are **not** declared.
- Purchase data is handled by Google Play itself; declare it because the app receives the purchase
  confirmation.

## Other App content items
- **Ads:** Yes, the app contains ads.
- **Privacy policy URL:** `https://trenbridgeit.com/trendoc/privacy-policy.html` — must be live
  before submitting (as of 2026-09-27 the domain does not resolve).
- **Target audience:** 18+ / general audience, not directed at children (matches policy §8).
