package com.rafa.hercules.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rafa.hercules.data.GymDao

import com.rafa.hercules.utils.ITimerManager

class SessionViewModelFactory(private val dao: GymDao, private val timerManager: ITimerManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SessionViewModel(dao, timerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
