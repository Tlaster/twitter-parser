# twitter-parser
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/moe.tlaster/twitter-parser/badge.svg)](https://maven-badges.herokuapp.com/maven-central/moe.tlaster/twitter-parser)
![license](https://img.shields.io/github/license/Tlaster/twitter-parser)

![badge-ios](https://img.shields.io/badge/Platform-iOS-lightgray)
![badge-jvm](https://img.shields.io/badge/Platform-JVM-orange)
![badge-js](https://img.shields.io/badge/Platform-JS-yellow)
![badge-macOS](https://img.shields.io/badge/Platform-macOS-purple)

A Kotlin multiplatform library to parse Twitter text. Like the [twitter-text](https://github.com/twitter/twitter-text).

# Setup
```
api("moe.tlaster:twitter-parser:$twitter_parser_version")
```
# Usage
```Kotlin
val parser = TwitterParser()
val content = "I'm using twitter parser by @MTlaster to prase twitter text #Kotlin "
val result = parser.parse(content)
```
The `result` will be:
```
[
  StringToken(value=I'm using twitter parser by ),
  UserNameToken(value=@MTlaster), 
  StringToken(value= to prase twitter text ), 
  HashTagToken(value=#Kotlin), StringToken(value= )
]
```

# LICENSE
```
MIT License

Copyright (c) 2022 Tlaster

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
