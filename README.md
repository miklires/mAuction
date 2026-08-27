<div align="center">

# mAuction

Fast, persistent player marketplace for Paper, Purpur, and Folia servers.

[![Paper](https://img.shields.io/badge/Available_for-Paper-222c31?style=for-the-badge)](https://papermc.io/software/paper)
[![Purpur](https://img.shields.io/badge/Available_for-Purpur-5f2167?style=for-the-badge)](https://purpurmc.org/)
[![Folia](https://img.shields.io/badge/Available_for-Folia-69c535?style=for-the-badge)](https://papermc.io/software/folia)

[![Build](https://img.shields.io/github/actions/workflow/status/miklires/mAuction/build.yml?label=build)](https://github.com/miklires/mAuction/actions)
![Release](https://img.shields.io/badge/release-v1.1.0-0ea5e9)
![Java](https://img.shields.io/badge/Java-25-5382a1)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a)

</div>

mAuction provides a clear inventory marketplace, fast item search, durable listings, and guarded Vault transactions. English is enabled by default; Russian can be selected with `language: ru_RU`.

## Features

- Browse with `/ah` or `/auction`, 45 listings per page, manual refresh, and four sort modes.
- Search item materials, custom display names, and seller names with `/ah search <name>`.
- Sell the held stack with `/ah sell <price>`; compact prices such as `10k`, `2.5m`, and `1b` are accepted.
- Open `/ah selling` to manage personal listings; clicking your own listing removes it safely.
- Live listing validation: bought, reserved, removed, and expired entries turn into gray dye in every open menu without closing it.
- Atomic database reservation prevents two buyers from purchasing the same listing.
- Staged buyer withdrawal, offline seller payout, configurable tax, and recovery of pending item deliveries.
- Automatic expiration and recovery of unsold items on login.
- Configurable listing limits, price range, cooldown, serialized-item size cap, and material blacklist.
- Async H2 persistence and Folia-safe player/inventory scheduling.
- English and Russian message files, optional bStats metrics, and admin reload.

## Commands

| Command | Purpose |
| --- | --- |
| `/ah`, `/auction` | Open the marketplace |
| `/ah search <name>` | Open filtered listings |
| `/ah sell <price>` | List the stack in the main hand |
| `/ah selling` | View and remove your listings |
| `/ah cancel <id>` | Remove a listing by ID |
| `/ah reload` | Reload configuration and messages |

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2
- Vault and any Vault-compatible economy provider

Vault acts as the economy bridge, so mAuction works with currency plugins that expose a Vault economy provider, including providers with optional Vault support.

## Quick start

1. Install Vault, an economy provider, and `mAuction-1.1.0.jar`.
2. Start the server once.
3. Adjust `plugins/mAuction/config.yml` if needed.
4. Hold an item and run `/ah sell 1000`.

The default configuration is ready to use. Set `language: ru_RU` for Russian.

## Build

Run `./gradlew clean build`. The release JAR is written to `build/libs/mAuction-1.1.0.jar`.

MIT licensed.
