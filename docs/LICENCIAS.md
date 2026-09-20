# Licencias

El código propio de AGENDA-D se distribuye bajo la licencia MIT; véase
[`LICENSE`](../LICENSE). Cada módulo Maven declara esa licencia en su `pom.xml`.

Las dependencias transitivas no se copian al repositorio. Antes de una entrega o
distribución se debe generar su inventario con Maven:

```bash
mvn -DincludeScope=runtime org.codehaus.mojo:license-maven-plugin:2.5.0:add-third-party
```

El archivo generado debe revisarse y conservarse junto al artefacto liberado.
