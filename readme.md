# Quarkus Procurement AI

A Quarkus-based AI-powered procurement assistant for Indonesian government procurement data (RUP - Rencana Umum Pengadaan). This application uses RAG (Retrieval Augmented Generation) with vector embeddings to provide intelligent answers about procurement records in Bahasa Indonesia.

## Features

- 🤖 **AI-Powered Chat**: Ask questions about procurement data in natural language
- 🔍 **Vector Search**: Semantic search through procurement records using embeddings
- 🇮🇩 **Indonesian Language Support**: Responds in Bahasa Indonesia
- 📊 **RUP Database Integration**: Works with Indonesian government procurement data
- 🚀 **High Performance**: Built on Quarkus for fast startup and low memory usage
- 🐘 **PostgreSQL + pgvector**: Efficient vector storage and retrieval

## Technologies Used

- **Framework**: Quarkus 3.15.1
- **Language**: Java 21
- **AI/ML**: LangChain4J 0.21.0
- **LLM**: Ollama (Qwen2.5:3b for chat, bge-m3 for embeddings)
- **Database**: PostgreSQL with pgvector extension
- **ORM**: Hibernate ORM with Panache
- **API**: JAX-RS with Jackson
- **Build Tool**: Maven

## Prerequisites

Before running this application, ensure you have:

1. **Java 21** or later
2. **Maven 3.8+**
3. **PostgreSQL** with **pgvector** extension
4. **Ollama** with required models:
   - `qwen2.5:3b` (for chat)
   - `bge-m3` (for embeddings)

## Setup Instructions

### 1. Database Setup

Create a PostgreSQL database with pgvector extension:

```sql
CREATE DATABASE procurement;
\c procurement;
CREATE EXTENSION vector;
```

### 2. Ollama Setup

Install and start Ollama, then pull the required models:

```bash
# Install Ollama (visit https://ollama.ai for installation instructions)

# Pull required models
ollama pull qwen2.5:3b
ollama pull bge-m3
```

### 3. Application Configuration

Update `src/main/resources/application.properties` with your database credentials:

```properties
# Database connection
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=your_username
quarkus.datasource.password=your_password
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/procurement

# Ollama Chat Config (Qwen)
quarkus.langchain4j.ollama.chat-model.model-id=qwen2.5:3b
quarkus.langchain4j.ollama.base-url=http://localhost:11434

quarkus.langchain4j.ollama.embedding-model.model-id=bge-m3

# PGVector Store Setup
quarkus.langchain4j.pgvector.table=procurement_record
quarkus.langchain4j.pgvector.dimension=1024
quarkus.langchain4j.pgvector.id-column=id
quarkus.langchain4j.pgvector.embedding-column=content_embedding
quarkus.langchain4j.pgvector.text-column=title
quarkus.langchain4j.pgvector.metadata-columns=institution,budget,year,category
```

### 4. Build and Run

```bash
# Build the application
./mvnw clean compile quarkus:dev

# Or run in production mode
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## API Documentation

### Chat Endpoint

Ask questions about procurement data:

**POST** `/procurement/chat`

```bash
curl -X POST http://localhost:8080/procurement/chat \
  -H "Content-Type: application/json" \
  -d "saya adalah vendor catering di jakarta, apa saja instansi yang mengadakan proyek konsumsi di tahun 2026 yang nominalnya besar?"
```

**Response:**
```
Sebagai vendor penyedia catering di Jakarta, ada beberapa instansi yang memiliki proyek dengan kategori Konsumsi & Catering dan budget yang besar pada tahun 2026, yaitu:

1. Provinsi DKI Jakarta (Belanja Makanan dan Minuman Rapat)
- Budget: Rp 84,500,000

2. Provinsi DKI Jakarta (Belanja Makanan dan Minuman Aktivitas Lapangan Uji Kompetensi Pegawai)
- Budget: Rp 309,710,000

Jadi, sebagai vendor catering di Jakarta, Anda bisa fokus menargetkan pekerjaan kepada Instansi Provinsi DKI Jakarta untuk kedua proyek-proyek tersebut. 
```

### Data Ingestion Endpoint

Process and embed procurement records:

**POST** `/procurement/ingest?limit={number}`

```bash
curl -X POST "http://localhost:8080/procurement/ingest?limit=100"
```

This endpoint processes unembedded procurement records and creates vector embeddings for semantic search.

## Data Model

The application works with procurement records containing:

- **idRup**: Unique RUP identifier
- **title**: Procurement title/description
- **budget**: Procurement budget
- **year**: Procurement year
- **institution**: Government institution details
- **category**: Procurement category
- **embedded**: Flag indicating if record has been vectorized

## Usage Examples

### 1. Ask about specific procurement items
```bash
curl -X POST http://localhost:8080/procurement/chat \
  -H "Content-Type: application/json" \
  -d "Apa saja proyek di pemprov DKI Jakarta untuk tahun 2026?"
```

### 2. Query budget information
```bash
curl -X POST http://localhost:8080/procurement/chat \
  -H "Content-Type: application/json" \
  -d "Berapa total anggaran pengadaan Kendaraan di Polri?"
```

### 3. Ingest new data
```bash
curl -X POST "http://localhost:8080/procurement/ingest?limit=50"
```

## Development

### Running in Development Mode

```bash
./mvnw compile quarkus:dev
```

This enables hot reload for faster development.

### Health Check

The application includes health checks available at:
- http://localhost:8080/q/health

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST Client   │───▶│  ChatResource    │───▶│ ProcurementAI   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │ EmbeddingService │    │     Ollama      │
                       └──────────────────┘    │   (Qwen2.5)     │
                                │              └─────────────────┘
                                ▼
                       ┌──────────────────┐
                       │   PostgreSQL     │
                       │   + pgvector     │
                       └──────────────────┘
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Author

Muhammad Edwin <edwin at redhat dot com>

---

For more information about Quarkus, visit: https://quarkus.io