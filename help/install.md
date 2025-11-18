# Restaurar codi "net"

Per restaurar el codi "net" i eliminar els canvis dels cursos anteriors:
```bash
./restore_src_backup.sh
```

# Fer anar les demos

Funcionament de Piomatter amb Java JNI:
```bash
./run.sh com.demos.DemoIETI
./run.sh com.demos.DemoAnim
```

Funcionament del projecte client-servidor:
```bash
# En una terminal, executar el servidor:
./run.sh com.project.server.Main
# En una altra terminal, executar el client:
./run.sh com.project.client.Client
```

A la linia de comandes del servidor, es poden afegir ordres per enviar dades al client:
```bash
/text Hola què tal?
/image ./src/main/resources/ietilogo.png
```
El client mostra les dades rebudes del servidor a la pantalla.

# Instal·lació de dependències (no cal)

```bash
# To compile and run Java JNI, lib and demos
sudo apt-get update
sudo apt-get install -y build-essential cmake git pkg-config
sudo apt-get install -y default-jdk
```
