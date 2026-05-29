# PerfWidget
Un widget de monitoreo de sistema minimalista, elegante y de alto rendimiento para Android. Creado en Kotlin, este proyecto lee métricas directamente desde el núcleo del sistema operativo para ofrecer estadísticas precisos en tiempo real sobre el estado de tu dispositivo.
Desarrollado como un reto personal de 24 horas, PerfWidget demuestra cómo construir un monitor de recursos ligero usando permisos de superusuario.
## Caracteristicas Principales
 * **Monitor de CPU:** Muestra el porcentaje de uso general, la frecuencia actual del núcleo principal y la temperatura termal del SoC.
 * **Memoria en Tiempo Real:** Visualiza el consumo actual de RAM y SWAP sobre el total disponible, con indicadores de colores según la carga.
 * **Almacenamiento (ROM):** Lectura instantánea del espacio utilizado frente al espacio total de la partición de datos.
 * **Estado de Batería:** Porcentaje de carga y temperatura exacta de la batería.
 * **Tráfico de Red:** Velocidad de descarga (D) y subida (U) en tiempo real, con ajuste dinámico de unidades (KB/s a MB/s).
 * **Uptime:** Tiempo exacto desde el último reinicio del sistema.
 * **Monitor de Servidores Custom:** Ping a direcciones IP y puertos específicos para saber si tus servidores personales están en línea (ON/OFF).
 * **Eficiencia:** Diseñado para actualizarse inteligentemente, pausando las consultas cuando la pantalla del dispositivo está apagada para ahorrar batería.
## Requisitos del Sistema
Para que el widget funcione correctamente, tu dispositivo debe cumplir con los siguientes requisitos:
 1. **SO:** Android 8.0 (Oreo) o superior.
 2. **Permisos Root:** Obligatorio. La aplicacion utiliza libsu para ejecutar comandos Shell y acceder a /proc y /sys.
 3. **Lanzador (Launcher):** Cualquier launcher que soporte widgets redimensionables.
## Notas sobre Compatibilidad (Dispositivo de Desarrollo)
Este proyecto fue desarrollado, calibrado y probado exitosamente bajo el siguiente entorno:
 * **Dispositivo:** Samsung Galaxy A04e
 * **Procesador:** MediaTek Helio P35 (MT6765)
 * **Memoria:** 3GB / 4GB RAM
 * **Sistema Operativo:** Android 12
 * **Condicion:** Dispositivo Rooteado (Magisk)
> **Aviso Importante sobre Rutas del Sistema:**
> Debido a la fragmentacion de Android y las diferencias en arquitecturas (Qualcomm, Exynos, MediaTek), las rutas de los archivos de hardware pueden variar.
> El lector de temperatura (/sys/class/thermal/thermal_zoneX/temp) y el escalador de frecuencia del CPU (/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) han sido adaptados basandose en el Samsung A04e. Si ejecutas esta app en un dispositivo de otra marca o con otro procesador, algunas metricas podrian mostrar "N/A" si las rutas del kernel difieren.
> 
## Instalacion y Uso
 1. Compila el proyecto usando Android Studio o tu entorno preferido.
 2. Instala el archivo .apk en tu dispositivo.
 3. Abre la aplicacion por primera vez. Se te solicitara conceder permisos de **Superusuario (Root)**.
 4. Una vez concedido el acceso, veras un mensaje confirmando la inicializacion.
 5. Ve a la pantalla de inicio de tu launcher, manten presionado, selecciona "Widgets" y busca **PerfWidget**.
 6. Arrastralo a tu pantalla. El servicio en primer plano iniciara automaticamente y los datos comenzaran a fluir.
## Configuracion de Servidores Propios
Puedes monitorear si tus servidores estan activos modificando el archivo UpdateService.kt antes de compilar.
Agrega tus IPs y puertos en la lista myServers:
```kotlin
private val myServers = listOf(
    Pair("192.168.1.100", 80),
    Pair("123.45.67.89", 6080)
)

```
Al tocar el widget en la pantalla de inicio, se forzara una actualizacion y un escaneo de estos servidores.
## Construido Con
 * **Kotlin:** Lenguaje de programacion principal.
 * **libsu (topjohnwu):** Para la ejecucion robusta de comandos Shell como Root.
 * **Android SDK:** AppWidgetProvider, Foreground Services, RemoteViews.
## Licencia
Este es un proyecto de codigo abierto de caracter personal. Sientete libre de bifurcarlo (fork), modificar las rutas termales para tu propio dispositivo y mejorarlo.
