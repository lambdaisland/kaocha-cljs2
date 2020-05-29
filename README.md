# kaocha-cljs2

<!-- badges -->
[![cljdoc badge](https://cljdoc.org/badge/lambdaisland/kaocha-cljs2)](https://cljdoc.org/d/lambdaisland/kaocha-cljs2) [![Clojars Project](https://img.shields.io/clojars/v/lambdaisland/kaocha-cljs2.svg)](https://clojars.org/lambdaisland/kaocha-cljs2)
<!-- /badges -->

Run ClojureScript tests with [Kaocha](https://github.com/lambdaisland/kaocha). New and improved.

<!-- opencollective -->
### Support Lambda Island Open Source

Kaocha-cljs2 is part of a growing collection of quality Clojure libraries and
tools released on the Lambda Island label. If you find value in our work please
consider [becoming a backer on Open Collective](http://opencollective.com/lambda-island#section-contribute)
<!-- /opencollective -->

## Introduction

Running ClojureScript tests is fundamentally different from running Clojure
tests in two ways: ClojureScript needs to be compiled first, and then it needs
to run inside a JavaScript environment like Node.js or a browser.

So there is a certain amount of preparation needed. First something invokes the
ClojureScript compiler, which outputs JavaScript, then something needs to launch
a JavaScript runtime (a browser, Node), and make sure that JavaScript gets
loaded into it.

Finally Kaocha needs to establish communication with that runtime, so that it
can invoke tests and collect results.

In the original `kaocha-cljs` we tried to do all of that. We relied on the
ClojureScript `repl-env` abstraction to do compilation behind the scenes, and to
launch a browser tab or node process, and communicate with it.

In `kaocha-cljs2` we have chosen a different approach. We no longer care about
how you compile your ClojureScript or how you launch your runtime. You can
automate that or do it manually, but it's your concern. We will of course
provide examples for common tools and use cases.

This means that we are forward compatible with any ClojureScript build tool,
current or future. We also no longer care about compiler settings like the
optimization level or how you manage dependencies and third party JavaScript.
All we ask is that you include a `:preload` in your build, so that we are able
to communicate with it.

## Cast of Characters

Because we have separated the differnent responsibilities to separate projects,
it does make it a little harder to keep track. These are the different pieces
involved.

### [Kaocha](https://github.com/lambdaisland/kaocha)

Extensible test runner. Written in Clojure, so runs on the JVM. Can run test
suites of many different types. Has CLI and REPL interfaces, and can be
customized with plugins.

### [Chui](https://github.com/lambdaisland/chui)

Test runner written in ClojureScript. Consists of `chui-core`, which provides
the test runner logic and API, `chui-ui` which provides a browser-based UI, and
`chui-remote`, which provides API access for running tests via Funnel. By adding
the remote to your ClojureScript build you allow it to talk to kaocha-cljs2.
Using the UI is optional.

### [Funnel](https://github.com/lambdaisland/funnel)

Stand-alone WebSocket message relay. Funnel runs in the background and acts as a
communication hub, providing discoverability (which `chui-remote` processes are
connected?), and enabling communication between `chui-remote` and
`kaocha-cljs2`.

### kaocha-cljs2

A Kaocha test suite type for ClojureScript test suites. Makes the Kaocha
ecosystem of tooling available to ClojureScript.

## Installation

### Add `lambdaisland/kaocha-cljs2` as a dependency:

``` clojure
;; deps.edn
lambdaisland/kaocha-cljs2 {:mvn/version "0.0"}
```

``` clojure
;; project.clj
[lambdaisland/kaocha-cljs2 "0.0"]
```

You also need [lambdaisland/chui-remote](https://github.com/lambdaisland/chui)
as a dependency so that it's available to your ClojureScript build.

### Install Funnel

Kaocha-cljs2 relies on Funnel, a websocket message relay, for communicating with
the JavaScript runtime.

Follow the instructions for installing and running
[Funnel](https://github.com/lambdaisland/funnel). We recommend starting Funnel
in a separate terminal and to leave it running indefinitely. Start it with `-vv`
initially so you can see what's going on.

``` shell
./funnel -vv
INFO [lambdaisland.funnel] {:started ["ws://localhost:44220" "wss://localhost:44221"], :line 328}
```

### Configure your ClojureScript build

Add `lambdaisland.chui.remote` as a
[preload](https://cljs.github.io/api/compiler-options/preloads) to your
ClojureScript compiler configuration. This will cause ClojureScript to "phone
home" when it gets loaded.

Where this goes exactly depends on the build tool you are using. You are looking
for a map containing keys like `:output-to`, `:main`, or `:optimizations`.

``` clojure
{;; :main foo.bar  ....
 :preloads [lambdaisland.chui.remote]}
```

Now verify that your ClojureScript build is "phoning home". Compile it and run
it (in a browser, node, ...) and look for a `:connection-opened` message in the
output from Funnel.

### Configure your test suite

``` clojure
;; tests.edn
#kaocha/v1
{:tests [{:type :kaocha.type/cljs2}]}
```

In principle this is all you need, this will look for any connected
`chui-remote` clients in the same project directory, so that if you are working
on multiple projects we connect to and run the tests from the right project.

To do this the current working directory gets injected into the ClojureScript
build, so if you are running the ClojureScript compiler from a separate process
then make sure it's running in the same directory where you are running Kaocha.

If you want Kaocha to kick off the ClojureScript compilation, and to launch a
JavaScript runtime, then it's recommended you do this with a before hook.

``` clojure
#kaocha/v1
{:plugins [:kaocha.plugin/hooks]
 :tests [{:type :kaocha.type/cljs2
          :kaocha.hooks/before [my.kaocha.hooks/compile-and-launch]}]}
```

See the examples directory for specific examples.

When kaoch-cljs2 starts executing it will try to find `chui-remote` clients to
talk to. If there are none it will wait for a client to connect before
continuing. If there are multiple then it will run tests against all of them.

You can change this behavior by supplying a `:kaocha.cljs2/clients-hook`. This
needs to be fully qualified symbol pointing at a function which takes a test
suite map and returns a collection of `:funnel/whoami` maps.

## License

Copyright &copy; 2020 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
