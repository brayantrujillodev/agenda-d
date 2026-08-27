## Qué hace

Cierra #

<!-- Dos o tres líneas. Qué cambia y por qué. -->

## Cómo lo probé

<!-- Comandos, tests que corrieron, o qué hiciste a mano.
     "Funciona en mi máquina" no cuenta. -->

- [ ] `docker compose --profile core up -d` sigue levantando bien
- [ ] Los tests pasan
- [ ] Probado a mano contra el backend real

## Revisión

- [ ] No validé solapamiento con un SELECT previo al INSERT
- [ ] No publiqué a Kafka fuera del outbox
- [ ] Usé `Instant`, no `LocalDateTime`
- [ ] Los mensajes de error están en español y son entendibles
- [ ] Si cambié el contrato OpenAPI, lo avisé en el grupo

## Para el revisor

<!-- ¿Qué parte quieres que miren con cuidado?
     Recuerda: te revisa alguien de la OTRA pareja. -->
