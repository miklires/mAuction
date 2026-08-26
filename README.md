<div align="center">

# mAuction

Transactional player auction house for Minecraft servers.

[![Paper](https://img.shields.io/badge/Available_for-Paper-222c31?style=for-the-badge)](https://papermc.io/software/paper)
[![Purpur](https://img.shields.io/badge/Available_for-Purpur-5f2167?style=for-the-badge)](https://purpurmc.org/)
[![Folia](https://img.shields.io/badge/Available_for-Folia-69c535?style=for-the-badge)](https://papermc.io/software/folia)

[![Build](https://img.shields.io/github/actions/workflow/status/miklires/mAuction/build.yml?label=build)](https://github.com/miklires/mAuction/actions)
![Release](https://img.shields.io/badge/release-v1.0.0-0ea5e9)
![Java](https://img.shields.io/badge/Java-25-5382a1)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a)

</div>

mAuction is a GUI auction house with durable listings, atomic reservations and journaled Vault payments.

## Features

- 54-slot auction GUI with item lore, seller, price and listing ID.
- `/ah sell <price>` and safe `/ah cancel <id>` item return.
- Atomic reservation prevents two players buying the same listing.
- Vault withdrawals, seller payments and configurable tax.
- H2 persistence, expiration, listing limits and purchase stages.
- Paper, Purpur and Folia 26.2 scheduling.

## Requirements

Vault and a Vault-compatible economy provider are required.

## Build

Run `./gradlew clean build`. The release JAR is written to `build/libs`.

MIT licensed.
