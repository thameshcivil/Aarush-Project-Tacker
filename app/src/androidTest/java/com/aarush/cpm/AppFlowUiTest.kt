package com.aarush.cpm

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_showsFieldsAndCreateAccountToggle() {
        composeRule.onNodeWithText("Username / Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Login").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        composeRule.onNodeWithText("Create account & continue").assertIsDisplayed()
    }

    @Test
    fun createAccount_thenReachDashboard() {
        composeRule.onNodeWithText("Create account").performClick()
        composeRule.onNodeWithText("Username").performTextInput("tester")
        composeRule.onNodeWithText("Email").performTextInput("tester@example.com")
        composeRule.onNodeWithText("Password").performTextInput("password123")
        composeRule.onNodeWithText("Create account & continue").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aarush CPM").assertIsDisplayed()
    }
}
