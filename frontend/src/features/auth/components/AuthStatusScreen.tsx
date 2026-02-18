import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";

interface AuthStatusScreenProps {
  title: string;
  description: string;
}

export function AuthStatusScreen({ title, description }: AuthStatusScreenProps) {
  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-1.5 w-full overflow-hidden bg-muted">
            <div className="h-full w-1/3 bg-primary animate-pulse" />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
