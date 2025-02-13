export interface TypedQuery<TResultType> {
  taxiQl: string
}

export interface TypedStream<TResultType> extends TypedQuery<TResultType> {
}

export interface CodegenConfig {
  documents: string[],
  taxiConf: string,
  outputDir: string,
  taxiSourcePath: string,
  watch: boolean
  orbitalServerUrl?: string
}

export interface QueryResult<TResultType = any> {
  data: TResultType | null;
  error: Error | null;
  loading: boolean;
  called: boolean;
}
