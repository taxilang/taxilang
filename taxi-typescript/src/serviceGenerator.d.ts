import { TypeMapper } from "./typeGenerator";
import { Service } from "./schema";
import { TypeHelper } from "./typeHelper";
export interface ServiceMapper {
    services: Service[];
}
export interface OperationProvider {
}
export declare class DefaultServiceMapper implements ServiceMapper {
    private typeHelper;
    private typeMapper;
    readonly services: Service[];
    constructor(typeHelper: TypeHelper, typeMapper: TypeMapper);
    private build;
    private generateService;
    private generateOperation;
}
export interface ServiceGeneratorFactory {
    build(typeHelper: TypeHelper, typeMapper: TypeMapper): ServiceMapper;
}
export declare class DefaultServiceGeneratorFactory implements ServiceGeneratorFactory {
    build(typeHelper: TypeHelper, typeMapper: TypeMapper): ServiceMapper;
}
