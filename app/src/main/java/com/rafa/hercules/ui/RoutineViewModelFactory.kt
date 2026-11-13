package com.rafa.hercules.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rafa.hercules.data.GymDao

class RoutineViewModelFactory(private val dao: GymDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoutineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoutineViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
