# carme

A simple Gemini server written in Clojure.

## What's a Gemini server?

Gemini is a lightweight protocol for serving content. Combine the protocol with the gemtext markup language and you get something without the huge weight, complexity and privacy concerns of the modern web.

See https://gemini.circumlunar.space for more information.

## Why should I use your server rather than any of the others?

No reason. Find one you like. Other ones probably have more features at the moment, but this one will work just fine for basic stuff.

## So why did you write it?

For fun. And to learn new things.

## Usage

Configuration is loaded from the `resources/config.edn` file.

You need a `keystore.jks` containing your server-side certificate in the directory from which you start the server.

> lein run

or

> lein uberjar
>
> java -jar target/carme-*-standalone.jar


## License

Copyright © 2022 Adam Turnbull

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
