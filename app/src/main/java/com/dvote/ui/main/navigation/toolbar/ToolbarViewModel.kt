package com.dvote.ui.main.navigation.toolbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.lifecycle.ViewModel
import com.dvote.ui.main.navigation.MainDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ToolbarViewModel @Inject constructor(

) : ViewModel() {

    private val _toolbarItems = MutableStateFlow<HashMap<MainDestinations, ToolbarItemData>>(hashMapOf())
    val toolbarItems = _toolbarItems.asStateFlow()

    init {
        initToolbarItems()
    }

    private fun initToolbarItems() {
        val toolbarItems = hashMapOf<MainDestinations, ToolbarItemData>()
        toolbarItems[MainDestinations.SurveysList] = ToolbarItemData(
            title = "Survey List",
            navigationIcon = Icons.Default.Menu,
            actionIcon = Icons.Default.Notifications,
        )
        toolbarItems[MainDestinations.Notifications] = ToolbarItemData(
            title = "Notifications",
            navigationIcon = null,
            actionIcon = null,
        )
        toolbarItems[MainDestinations.Notifications] = ToolbarItemData(
            title = "Settings",
            navigationIcon = null,
            actionIcon = null,
        )
        _toolbarItems.value = toolbarItems
    }

}