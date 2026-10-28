package pe.edu.upeu.examen

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform