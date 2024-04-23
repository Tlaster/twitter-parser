package moe.tlaster.twitter.parser.tokenizer

internal enum class TokenCharacterType {
    Eof,
    Character,
    Url,
    Cash,
    UserName,
    HashTag,
    Emoji,

    UnKnown,
}