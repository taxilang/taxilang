import * as React from 'react';
import {BigText, SectionHeading, SectionHeadingParagraph} from '@/components/common';
import {Snippet} from '@/components/Steps';
import PlaygroundSnippet from '@/components/PlaygroundSnippet';

interface IntegrationComparisonProps {
  highlightedJsCode: string;
}

const javascriptCodeSnippet = {
  name: 'integration.js',
  lang: 'javascript',
  code: `// Without Taxi: The integration code you actually write
async function getOrdersWithDetails(customerId) {
  const customer = await customerApi.getCustomer(customerId);
  const orders = await orderApi.getOrdersByCustomer(customerId);

  const enrichedOrders = await Promise.all(
    orders.map(async (order) => {
      const [shipping, payment] = await Promise.all([
        shippingApi.getTracking(order.trackingId).catch(() => null),
        paymentApi.getPayment(order.paymentId).catch(() => null),
      ]);

      return {
        orderId: order.id,
        total: order.total,
        customerName: customer.name,
        shippingStatus: shipping?.status ?? 'unknown',
        paymentMethod: payment?.method,
      };
    })
  );

  return enrichedOrders;
}`
};

const taxiScenario = {
  schema: `import CustomerId

closed model Order {
   id : OrderId inherits String
   orderTotal: OrderTotal inherits Decimal
   customerId: CustomerId inherits String
}

model Customer {
   id : CustomerId
   name : CustomerName inherits String
}

service CustomerApi {
   operation findCustomer(CustomerId):Customer
}

service OrderApi {
   operation findOrders():Order[]
}`,
  query: `// With TaxiQL: Taxi writes it for you
find { Order[] } as {
  orderId: OrderId
  total: OrderTotal
  customerName: CustomerName  // Taxi discovers this
}[]`,
  parameters: {},
  stubs: [],
  readme: "",
  layout: {
    showDiagram: true,
    showReadme: false,
    showQuery: true,
    showSchema: false
  }
};

export const IntegrationComparison: React.FC<IntegrationComparisonProps> = ({highlightedJsCode}) => {
  return (
    <div className="max-w-7xl mx-auto p-4 py-16">
      <div className="text-center text-lg mb-12">
        <BigText>Describe what you want. Taxi handles the rest.</BigText>
        <SectionHeadingParagraph>
          <p>
            Traditional API integration requires writing and maintaining complex orchestration code.
            With Taxi, you simply describe the data you need, and Taxi automatically discovers how to fetch it.
          </p>
        </SectionHeadingParagraph>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
        {/* Left side: JavaScript integration code */}
        <div className="flex flex-col">
          <h3 className="text-xl font-semibold text-slate-200 mb-4">
            Without Taxi
          </h3>
          <Snippet
            code={javascriptCodeSnippet}
            highlightedCode={highlightedJsCode}
          />
          <p className="mt-4 text-sm text-slate-400">
            Manual orchestration code that needs maintenance when APIs change
          </p>
        </div>

        {/* Right side: Taxi query */}
        <div className="flex flex-col">
          <h3 className="text-xl font-semibold text-slate-200 mb-4">
            With Taxi
          </h3>
          <PlaygroundSnippet
            title=""
            scenario={taxiScenario}
            primaryRunButton={true}
            showQueryPlan={true}
          >
            <PlaygroundSnippet.Description>
              Taxi automatically discovers `CustomerName` by joining across services using semantic types. Click "Show Query Plan" to see how Taxi builds the integration.
            </PlaygroundSnippet.Description>
          </PlaygroundSnippet>
          <p className="mt-4 text-sm text-slate-400">
            Declarative query that adapts automatically as your APIs evolve
          </p>
        </div>
      </div>
    </div>
  );
};
