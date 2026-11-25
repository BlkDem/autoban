package com.blkdem.autoban.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandsTest {
    @Test fun parse() {
        assertEquals(BotCommand.Help, BotCommand.parse("/start"))
        assertEquals(BotCommand.Stats, BotCommand.parse("/stats@maxautobanbot"))
        assertEquals(BotCommand.Last(10), BotCommand.parse("/last"))
        assertEquals(BotCommand.Last(5), BotCommand.parse("/last 5"))
        assertEquals(BotCommand.Last(50), BotCommand.parse("/last 999"))
        assertEquals(BotCommand.Ban("+79123456789"), BotCommand.parse("/ban 8 (912) 345-67-89"))
        assertEquals(BotCommand.Ban("+7495*"), BotCommand.parse("/ban +7495*"))
        assertEquals(BotCommand.Ban("+7495*"), BotCommand.parse("/ban +7 (495) *"))
        assertEquals(BotCommand.Unban("+79123456789"), BotCommand.parse("/UNBAN +79123456789"))
        assertEquals(BotCommand.White("+79123456789"), BotCommand.parse("/white 9123456789"))
        assertTrue(BotCommand.parse("/ban") is BotCommand.Unknown)
        assertTrue(BotCommand.parse("/ban re:(") is BotCommand.Unknown)
        assertTrue(BotCommand.parse("/foo") is BotCommand.Unknown)
        assertNull(BotCommand.parse("привет"))
    }

    @Test fun messageBodyWithButtons() {
        val body = TelegramClient.messageBody(
            "42", "hi", silent = true,
            buttons = listOf(TelegramClient.Button("🔓 Разбанить", "unban:+79123456789"), TelegramClient.Button("✅", "white:+79123456789")),
        ).toString()
        assertEquals(
            """{"chat_id":"42","text":"hi","parse_mode":"HTML","disable_web_page_preview":true,"disable_notification":true,""" +
                """"reply_markup":{"inline_keyboard":[[{"text":"🔓 Разбанить","callback_data":"unban:+79123456789"},{"text":"✅","callback_data":"white:+79123456789"}]]}}""",
            body,
        )
        assertTrue("unban:+79123456789".toByteArray().size <= 64)
        assertTrue(!TelegramClient.messageBody("1", "x", false, emptyList()).toString().contains("reply_markup"))
    }

    @Test fun callbacks() {
        assertEquals(BotCommand.Ban("+79123456789"), BotCommand.fromCallback("ban:+79123456789"))
        assertEquals(BotCommand.Unban("+79123456789"), BotCommand.fromCallback("unban:89123456789"))
        assertEquals(BotCommand.White("+79123456789"), BotCommand.fromCallback("white:+79123456789"))
        assertNull(BotCommand.fromCallback("drop:+79123456789"))
        assertNull(BotCommand.fromCallback("ban:"))
        assertNull(BotCommand.fromCallback("garbage"))
    }
}
