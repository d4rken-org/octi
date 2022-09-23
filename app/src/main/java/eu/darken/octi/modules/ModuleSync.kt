package eu.darken.octi.modules


interface ModuleSync<T : Any> {

    val moduleId: ModuleId

    fun start()
}