package com.dvote.ui.main.vote

data class VoteRecord(
    val choices: List<String>   = emptyList(),
    val timestamp: Long         = System.currentTimeMillis()
)

data class VoteRecordWithUser(
    val userId: String,
    val choiceId: String
)