from app import create_app

app = create_app()

if __name__ == "__main__":
    # Solo desarrollo. En producción va detrás de un proxy con HTTPS/TLS 1.2+ (RNF-03).
    app.run(host="0.0.0.0", port=5000)
