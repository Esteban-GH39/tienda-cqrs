# Publicar el proyecto y la wiki en GitHub

## 1. Subir el código

1. En GitHub: **New repository** (por ejemplo `tienda-cqrs`), sin README ni .gitignore (ya vienen en el proyecto).
2. En la carpeta del proyecto:

```
git init
git add .
git commit -m "Proyecto tienda en vivo: CQRS + Kafka + RabbitMQ + WebSocket"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/tienda-cqrs.git
git push -u origin main
```

Antes de hacer `git add .`, comprueba que **no** exista un archivo `.env` con tu clave de Supabase (está en `.gitignore`, pero revisa).

## 2. Publicar la wiki

La wiki de GitHub es un repositorio aparte.

1. En el repositorio: **Settings → Features → Wikis** (activada).
2. Pestaña **Wiki → Create the first page**: guarda cualquier cosa (esto crea el repositorio de la wiki).
3. Clona la wiki, copia las páginas y súbelas:

```
git clone https://github.com/TU_USUARIO/tienda-cqrs.wiki.git
copy docs\wiki\*.md tienda-cqrs.wiki\        (Windows)
cd tienda-cqrs.wiki
git add .
git commit -m "Wiki del proyecto"
git push
```

En Linux o Mac usa `cp docs/wiki/*.md tienda-cqrs.wiki/`.

## 3. Requerimientos

Ya están en `docs/REQUERIMIENTOS.md` y se ven desde el README. Si el profesor pide que estén en la wiki, copia ese archivo a la carpeta de la wiki como `Requerimientos.md`.

## 4. Comprobar

- La pestaña **Actions** debe mostrar el CI en verde (compila y prueba servidor y clientes).
- El diagrama del README se dibuja solo (GitHub soporta Mermaid).
