# Revisión de GitHub Actions

Revisión aplicada con `github-actions-hardening`, sobre el flujo previo a 0.4.0.

| Severidad | Hallazgos |
| --- | ---: |
| Crítica | 0 |
| Alta | 2 referencias de terceros mutables |
| Media | 0 |
| Baja | 3 referencias propias mutables; credencial persistente de checkout |

En `.github/workflows/android.yml`, `android-actions/setup-android@v3` y `gradle/actions/setup-gradle@v4` permitían cambiar el código ejecutado sin modificar este repositorio. Se fijaron a commits completos, igual que checkout, Java y artefactos. Antes: `uses: gradle/actions/setup-gradle@v4`. Después: `uses: gradle/actions/setup-gradle@41ad77ca97e46ee2e825d5e6b9a95d8169fcc44c`.

Los triggers push/pull_request no usaban contextos privilegiados y `contents: read` era correcto. Se conserva ese límite y se establece `persist-credentials: false`. No se interpolan títulos ni texto de PR en comandos. La entrega manual solo se ejecuta desde main. El job que compila no recibe claves: el job de firma consume los binarios del mismo workflow y no ejecuta Gradle con secretos presentes. Las claves se exponen únicamente al paso de firma, se comprueban las huellas públicas esperadas y se limpian los temporales incluso ante error. Falta validar la primera entrega con las claves del propietario.

La skill es una guía de revisión; las correcciones se aplican dentro de la implementación solicitada y aprobada por el usuario.
