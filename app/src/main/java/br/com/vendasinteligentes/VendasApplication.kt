package br.com.vendasinteligentes

import android.app.Application
import br.com.vendasinteligentes.data.local.AppDatabase
import br.com.vendasinteligentes.data.repository.VendasRepository

class VendasApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: VendasRepository by lazy { VendasRepository(database) }
}
