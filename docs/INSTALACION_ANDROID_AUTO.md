# Installing HomeCar on Android Auto / Instalación en Android Auto

> **RC2 is a pre-release, not an officially approved Android Auto application.** Installer origin tricks may help on some devices but do not remove all Android Auto validation restrictions. Avoid rooting a primary phone solely for this app.

## Español — Instalación de HomeCar 1.0.0 RC2

### 1. Descarga correcta

Abre https://github.com/RenanEspinar/HomeCar/releases y selecciona **v1.0.0-rc.2**. En **Assets**, descarga `HomeCar-1.0.0-RC2.apk`. **No descargues** "Source code (zip)" para instalar en el teléfono. Si quieres volver a una versión anterior, consulta la publicación histórica correspondiente en Releases.

### 2. Primer intento: instalación convencional

Instala el APK como cualquier aplicación Android. Abre HomeCar **en el teléfono**, entra en Ajustes, configura tu URL de Home Assistant (HTTPS recomendado), inicia sesión y verifica que el dashboard cargue.

En Ajustes busca **Android Auto → Personalizar lanzador**. Si HomeCar aparece, actívalo. Si no aparece, sigue con KingInstaller.

### 3. Método alternativo: KingInstaller + Shizuku (sin root)

Descarga ambas herramientas desde sus repositorios originales:

- KingInstaller: https://github.com/fcaronte/KingInstaller/releases
- Shizuku: https://github.com/RikkaApps/Shizuku/releases

Con una red Wi-Fi disponible (puede ser un hotspot; no es necesario acceso a Internet para el emparejamiento local), abre Shizuku. En las **Opciones de desarrollador** de Android activa **Depuración inalámbrica**, elige **Emparejar dispositivo con código** e introduce el código mostrado por Android en la notificación de Shizuku. Después vuelve a Shizuku y pulsa **Iniciar**. Debe indicar que está en ejecución. En algunos dispositivos o versiones de Android los pasos pueden variar.

Abre KingInstaller, concede autorización para usar Shizuku si se solicita, activa **Shizuku Trick**, selecciona `HomeCar-1.0.0-RC2.apk` e instálalo. Los nombres de controles pueden variar entre versiones. KingInstaller recomienda intentar primero su instalación normal y usar Shizuku cuando falle la visibilidad.

Revisa **Android Auto → Ajustes de desarrollador → Fuentes desconocidas** si esta opción está disponible, y **Personalizar lanzador**. Reconecta Android Auto con el vehículo estacionado. En un entorno real, el mantenedor pudo abrir RC2 mediante este procedimiento; **no significa compatibilidad universal**.

### 4. Problemas frecuentes

- **Instala, pero no aparece en el lanzador:** no insistas cambiando instaladores indefinidamente. Android Auto puede bloquear la categoría de proyección; consulta el [issue #9](https://github.com/RenanEspinar/HomeCar/issues/9). Registra modelo, sistema y versión de Android Auto.
- **Shizuku dejó de funcionar:** en Android sin root normalmente debes volver a iniciarlo tras reiniciar el teléfono.
- **No entra a Home Assistant:** comprueba conectividad, DNS, dirección del servidor, certificado HTTPS y sesión. No publiques tokens ni direcciones privadas al abrir un issue.
- **Error de actualización de APK:** versiones firmadas con claves distintas pueden requerir desinstalar. La desinstalación borra los datos locales de esa aplicación; prepara tus credenciales y configura de nuevo.
- **Dos iconos de HomeCar:** pueden corresponder a aplicaciones con identificadores distintos. Verifica paquete y versión desde los ajustes del sistema antes de borrar nada.
- **Fotos/vídeos:** RC2 intenta eliminar permisos heredados del flavor Android Auto. Si vuelven a pedirse, adjunta versión, captura y resultado sin datos sensibles.
- **mTLS:** elegir un certificado de cliente depende de KeyChain y de un certificado correctamente instalado; hay variaciones por servidor y dispositivo.

### 5. Seguridad y legalidad

HomeCar y KingInstaller son proyectos independientes de Google. Algunas instalaciones dependen de métodos no oficiales que pueden dejar de funcionar tras actualizar Android Auto/Play Services. No uses la aplicación para accionar puertas, portones, alarmas o cámaras mientras conduces. Prueba todo con el coche estacionado.

## English — Quick installation

1. Go to [GitHub Releases](https://github.com/RenanEspinar/HomeCar/releases), open **v1.0.0-rc.2**, and download the `.apk` asset (not source ZIP).
2. Try a regular APK installation first; open HomeCar on the phone, configure the Home Assistant address, sign in and check Android Auto's **Customize launcher**.
3. If missing, obtain [KingInstaller](https://github.com/fcaronte/KingInstaller/releases) and [Shizuku](https://github.com/RikkaApps/Shizuku/releases) from their official GitHub repositories.
4. Start Shizuku using Wireless debugging pairing (Wi-Fi required for standard Android wireless debugging). Authorize KingInstaller to access Shizuku, enable **Shizuku Trick**, select the HomeCar APK and reinstall.
5. Check Android Auto developer settings for **Unknown sources** if available; reconnect while parked. Android Auto may still reject unsupported projection apps.
6. If it remains absent, report device and Android Auto details on [GitHub Issues](https://github.com/RenanEspinar/HomeCar/issues) rather than repeatedly uninstalling. Protect your session data and backup your credentials.

The current RC2 APK is **debug-signed**. A future APK with a different signing certificate cannot update it in place. There is no automatic installation of updates.
