# Discord News Bot

## Objetivo da Aplicação

O Discord News Bot é uma aplicação Spring Boot que automatiza a coleta, processamento e envio de notícias de tecnologia e jogos para um canal do Discord. A aplicação busca notícias de diversas fontes RSS, filtra duplicatas, seleciona as mais relevantes usando inteligência artificial, traduz para português (quando necessário) e envia para o Discord através de webhooks.

## Funcionalidades Principais

- Coleta automática de notícias de múltiplas fontes RSS
- Detecção e filtragem de notícias duplicadas
- Seleção inteligente das notícias mais relevantes usando IA
- Tradução automática para português
- Envio formatado para o Discord com embeds
- Agendamento diário para envio automático
- API para execução manual do processo

## Requisitos

- Java 17 ou superior
- PostgreSQL (ou usar configuração local com R2DBC)
- Chave de API da OpenAI (para tradução e seleção de notícias)
- URL de webhook do Discord

## Como Instalar e Rodar

### Configuração do Banco de Dados

Você pode usar o PostgreSQL em um container Docker ou configurar uma conexão local:

```bash
# Usando Docker
docker run --name newsbot-postgres -e POSTGRES_PASSWORD=senha123 -e POSTGRES_USER=newsbot_run -e POSTGRES_DB=newsbot -p 5432:5432 -d postgres
```

Ou configure um banco de dados em memória para testes locais modificando o `application.yaml`.

### Configuração da Aplicação

1. Clone o repositório:
```bash
git clone https://github.com/seu-usuario/discord-news-bot.git
cd discord-news-bot
```

2. Configure as variáveis de ambiente ou ajuste o arquivo `application.yaml`:
   - `DISCORD_URL`: URL do webhook do Discord
   - `openai.api-key`: Chave de API da OpenAI

3. Compile a aplicação:
```bash
./gradlew build
```

4. Execute a aplicação:
```bash
./gradlew bootRun
```

Ou execute o JAR gerado:
```bash
java -jar build/libs/discord-news-bot-0.0.1-SNAPSHOT.jar
```

### Configuração Local Alternativa

Para executar localmente sem PostgreSQL, você pode modificar o `application.yaml` para usar H2 em memória:

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///newsbot;DB_CLOSE_DELAY=-1
    username: sa
    password: 
```

## Como Funciona o Fluxo de Envio de Notícias

1. **Coleta de Notícias**: A aplicação busca notícias de todas as fontes RSS configuradas.

2. **Filtragem de Duplicatas**: As notícias são filtradas para remover duplicatas, usando três métodos:
   - Verificação de URL duplicada
   - Verificação de hash de conteúdo
   - Análise de similaridade de conteúdo

3. **Seleção de Notícias Relevantes**: Um modelo de IA avalia e classifica as notícias com base em sua relevância, selecionando as melhores.

4. **Processamento e Tradução**: As notícias selecionadas são processadas:
   - Tradução para português (se necessário)
   - Resumo do conteúdo
   - Formatação para envio ao Discord

5. **Envio para o Discord**: As notícias são enviadas em lotes para o Discord, respeitando os limites da API.

6. **Registro no Banco de Dados**: As notícias enviadas são marcadas como processadas para evitar duplicação.

## Execução Manual

Você pode acionar o processo manualmente através da API:

```bash
curl -X POST http://localhost:8443/api/news/execute
```

## Agendamento

Por padrão, o job está configurado para executar diariamente às 11:00 (horário de São Paulo). Você pode ajustar este agendamento no arquivo `DailyNewsJob.java`.

## Personalização

Você pode personalizar a aplicação ajustando as configurações no arquivo `application.yaml`:

- Fontes RSS (`app.news.rss-feeds`)
- Número de notícias a serem selecionadas (`app.news.top-news-count`)
- Cor dos embeds no Discord (`app.discord.embed-color`)
- Limites de tamanho para títulos e descrições (`app.discord.max-description-length`)
- Limiar de similaridade para detecção de duplicatas (`app.similarity.threshold`)