# Changelog

## 1.2.0 - 2026-09-04

- Added database-side search, sorting, pagination, seller-limit enforcement, indexes, and schema versioning.
- Added purchase confirmation and `/ah collect` for pending item and payout recovery.
- Added journaled withdrawal, payout, and delivery stages with restart-safe reservation maintenance.
- Added append-only transaction events and `/ah audit` for uncertain external-operation stages.
- Replaced new item storage with Paper byte serialization and retained a bounded legacy migration reader.
- Added strict configuration validation, exact locale selection, corrupt-item handling, and safe full-inventory behavior.

## 1.1.0 - 2026-08-27

- Added `/ah search <name>`, `/ah selling`, pagination, and sorting.
- Added live menu validation and gray outdated-listing markers.
- Added English-default and Russian-selectable message files.
- Added recovery for expired, cancelled, and pending purchased items.
- Added compact price input, listing cooldown, material blacklist, and item-size limits.
- Added drag protection and configurable bStats.

## 1.0.0 - 2026-08-26

- Added persistent GUI auction listings.
- Added atomic purchase reservation and staged Vault payments.
- Added listing limits, expiration, cancellation and configurable tax.
- Added Paper, Purpur and Folia 26.2 support.
