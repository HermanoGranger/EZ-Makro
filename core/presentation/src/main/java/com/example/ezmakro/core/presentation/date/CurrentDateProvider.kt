package com.example.ezmakro.core.presentation.date

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class CurrentDateProvider {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    fun setDate(newDate: LocalDate) {
        _date.value = newDate
    }

    fun setToToday() {
        _date.value = LocalDate.now()
    }
}
