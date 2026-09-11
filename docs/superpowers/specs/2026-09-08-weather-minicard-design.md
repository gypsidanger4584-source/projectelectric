# Weather Mini-Card Live Integration Design

Date: 2026-09-08  
Scope: `index.html` weather mini-card only  
Status: Approved (approach A)

## Goal

Replace hard-coded `24°C` / `구름 조금 · Seoul` with live temperature, condition text, place name, and matching lucide icon. Do not change any other UI (layout, classes on other cards, GSAP, map markup).

## Constraints

- Client-only; no Spring controller / yml / new Java classes.
- Open-Meteo for weather (no API key).
- Location: browser GPS if allowed, else Seoul City Hall (`37.566826`, `126.9786567`).
- Place name: BigDataCloud reverse-geocode client API; on failure use `Seoul`.
- Touch only the weather `<article>` content nodes (add stable `data-*` / ids if needed for selectors).

## Architecture

```
DOMContentLoaded
  └─ initWeather()
       ├─ resolveCoords()  → GPS or Seoul fallback
       ├─ fetch Open-Meteo current weather
       ├─ fetch BigDataCloud reverse place name (best-effort)
       └─ patchWeatherCard(temp, label, city, icon)
```

Independent from `initLiveMap()` (no shared mutable state required).

## Data Sources

### Open-Meteo

`GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lng}&current=temperature_2m,weather_code`

Use `current.temperature_2m` (display as integer °C) and `current.weather_code` (WMO).

### Place name

`GET https://api.bigdatacloud.net/data/reverse-geocode-client?latitude={lat}&longitude={lng}&localityLanguage=ko`

Prefer `city` or `locality` or `principalSubdivision`; fallback `Seoul`.

## WMO → UI Mapping

Map weather_code ranges to Korean short label + lucide icon name, e.g.:

| code | label | icon |
|------|-------|------|
| 0 | 맑음 | sun |
| 1–3 | 구름 조금 / 흐림 | cloud-sun / cloud |
| 45, 48 | 안개 | cloud-fog |
| 51–67 | 비 | cloud-rain |
| 71–77 | 눈 | snowflake |
| 80–82 | 소나기 | cloud-rain |
| 95–99 | 뇌우 | cloud-lightning |

Exact table finalized in implementation; unknown → `흐림` + `cloud`.

## DOM Patch (weather article only)

Current structure stays; add selectors only:

- Temperature `<p>` → text `{n}°C`
- Subline `<p>` → `{label} · {city}`
- Icon `<i data-lucide="...">` → update `data-lucide`, then `lucide.createIcons()` scoped or global refresh

No change to article classes, padding, siblings, or `#dash-bottom` grid.

## Error Handling

- GPS deny/error → Seoul coords, continue.
- Open-Meteo fail → leave existing placeholder text (or last good values); `console.error` only.
- Reverse-geocode fail → city = `Seoul`.
- Never throw uncaught; weather failure must not break map/GSAP.

## Out of Scope

- `map.html`, other controllers, User lat/lng persistence.
- Caching, Redis, Kafka, AWS.
- Redesign of weather card visuals.

## Success Criteria

1. On load with GPS allow: card shows real temp + Korean condition + local city.
2. On GPS deny: Seoul weather + `Seoul` (or reverse name for Seoul coords).
3. No visual/layout change outside that article’s text/icon.
4. Map and other dashboard cards unchanged.
