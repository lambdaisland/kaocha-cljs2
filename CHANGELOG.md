# Unreleased

## Added

## Fixed

- Fix #23 dead link in readme

## Changed

# 0.1.58 (2022-11-11 / 98fdc42)

## Added

## Fixed

- Upgrade Chui, fixes a glogi dependency incompatibility with current version of Clojurescript

## Changed

- Upgrade Kaocha to 1.70.1086

# 0.0.35 (2020-10-02 / 3a506bd)

## Fixed

- Fix fixtures :once :after hook

# 0.0.28 (2020-08-27 / 9ed88aa)

## Added

- Make the timeout configurable with `:kaocha.cljs2/timeout` in miliseconds. If
  no Funnel message has been received for this amount of time then we assume the
  client has gotten stuck or gone away and we time out

## Fixed

- Upgrade Chui, this fixes handling of `:once` fixtures

# 0.0.21 (2020-08-19 / d4de44c)

## Fixed

- Remove debug call
- Version bumps

# 0.0.18 (2020-08-19 / 239b9b8)

## Fixed

- Add chui-ui dependency

# 0.0.15 (2020-08-19 / d6f3fd1)

First pre-release.
